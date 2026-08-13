//
//  AudiobookPlayerController.swift
//  Reader
//
//  Created by Aryan Raj on 09/08/26.
//
//  Native AVPlayer-backed audiobook playback. Kotlin directs playback through
//  ReaderIosBridge handlers and receives state updates via onPlaybackUpdate.
//

import AVFoundation
import Foundation

class AudiobookPlayerController {
    private var player: AVPlayer?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var failureObserver: NSObjectProtocol?
    private var statusObserver: NSKeyValueObservation?
    private var sleepTimer: Timer?
    private var sleepTickTimer: Timer?
    private var sleepRemainingSeconds: Int64 = 0
    private var pendingSeekMs: Double?
    private var isLoading = false
    private var currentSpeed: Float = 1

    /// Invoked from the main thread on every playback state change (tick,
    /// pause, resume, seek, sleep-timer tick, end or error updates).
    var onPlaybackUpdate: (
        (_ isPlaying: Bool,
         _ isLoading: Bool,
         _ positionMs: Int64,
         _ durationMs: Int64,
         _ speed: Float,
         _ sleepTimerRemainingMs: Int64,
         _ error: String?) -> Void
    )?

    func play(filePath: String, positionMs: Double, speed: Double) {
        stopInternal()
        guard FileManager.default.fileExists(atPath: filePath) else {
            pushUpdate(isPlaying: false, isLoading: false, error: "Could not find this audiobook file")
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.allowAirPlay])
        try? AVAudioSession.sharedInstance().setActive(true)
        let url = URL(fileURLWithPath: filePath)
        let item = AVPlayerItem(url: url)
        let newPlayer = AVPlayer(playerItem: item)
        player = newPlayer
        isLoading = true
        pendingSeekMs = positionMs > 0 ? positionMs : nil
        currentSpeed = Float(speed) > 0 ? Float(speed) : 1
        installObservers(for: item)
        if positionMs > 0 {
            newPlayer.seek(to: CMTime(seconds: positionMs / 1000.0, preferredTimescale: 1000))
        }
        newPlayer.rate = Float(speed)
        newPlayer.play()
        pushUpdate(isPlaying: true, isLoading: true)
    }

    func pause() {
        player?.pause()
        pushUpdate(isPlaying: false, isLoading: false)
    }

    func resume(speed: Float) {
        guard let player = player else { return }
        let safeSpeed = speed > 0 ? speed : 1
        currentSpeed = Float(safeSpeed)
        if let pendingSeekMs = pendingSeekMs {
            player.seek(to: CMTime(seconds: pendingSeekMs / 1000.0, preferredTimescale: 1000))
        }
        self.pendingSeekMs = nil
        player.rate = Float(safeSpeed)
        player.play()
        pushUpdate(isPlaying: true, isLoading: false)
    }

    func seek(to positionMs: Double) {
        guard let player = player else { return }
        let position = max(positionMs, 0)
        if isLoading {
            pendingSeekMs = position
            return
        }
        player.seek(to: CMTime(seconds: position / 1000.0, preferredTimescale: 1000))
        pushUpdate(isPlaying: player.timeControlStatus == .playing, isLoading: false)
    }

    func setSpeed(_ speed: Double) {
        guard let player = player else { return }
        let safeSpeed = speed > 0 ? speed : 1
        currentSpeed = Float(safeSpeed)
        if player.timeControlStatus == .playing {
            player.rate = Float(safeSpeed)
        }
        pushUpdate(isPlaying: player.timeControlStatus == .playing, isLoading: false)
    }

    func setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepRemainingSeconds = Int64(max(minutes, 1) * 60)
        let seconds = TimeInterval(sleepRemainingSeconds)
        sleepTimer = Timer.scheduledTimer(withTimeInterval: seconds, repeats: false) { [weak self] _ in
            self?.sleepTimer = nil
            self?.sleepRemainingSeconds = 0
            self?.player?.pause()
            self?.pushUpdate(isPlaying: false, isLoading: false)
        }
        sleepTickTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] timer in
            guard let self = self else { return }
            if self.sleepRemainingSeconds > 1 {
                self.sleepRemainingSeconds -= 1
                self.pushUpdate(isPlaying: self.player?.timeControlStatus == .playing, isLoading: false)
            } else {
                timer.invalidate()
            }
        }
        pushUpdate(isPlaying: player?.timeControlStatus == .playing, isLoading: false)
    }

    func cancelSleepTimer() {
        sleepTimer?.invalidate()
        sleepTimer = nil
        sleepTickTimer?.invalidate()
        sleepTickTimer = nil
        if sleepRemainingSeconds > 0 {
            sleepRemainingSeconds = 0
            pushUpdate(isPlaying: player?.timeControlStatus == .playing, isLoading: false)
        }
    }

    func stop() {
        stopInternal()
    }

    private func stopInternal() {
        if let timeObserver = timeObserver {
            player?.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
        if let endObserver = endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        endObserver = nil
        if let failureObserver = failureObserver {
            NotificationCenter.default.removeObserver(failureObserver)
        }
        failureObserver = nil
        statusObserver?.invalidate()
        statusObserver = nil
        cancelSleepTimer()
        player?.pause()
        player = nil
        isLoading = false
        pendingSeekMs = nil
    }

    private func installObservers(for item: AVPlayerItem) {
        let center = NotificationCenter.default
        endObserver = center.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            guard let self = self else { return }
            let duration = self.itemDurationMs
            self.isLoading = false
            self.pushUpdate(isPlaying: false, isLoading: false, positionMs: duration)
        }
        failureObserver = center.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] notification in
            let error = (notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error)?.localizedDescription
            self?.isLoading = false
            self?.pushUpdate(isPlaying: false, isLoading: false, error: error ?? "Could not play this audiobook")
        }
        statusObserver = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            guard let self = self else { return }
            if item.status == .failed {
                self.isLoading = false
                self.pushUpdate(
                    isPlaying: false,
                    isLoading: false,
                    error: item.error?.localizedDescription ?? "Could not play this audiobook"
                )
            }
        }
        timeObserver = player?.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600),
            queue: .main
        ) { [weak self] _ in
            guard let self = self else { return }
            self.isLoading = false
            self.pushUpdate(isPlaying: self.player?.timeControlStatus == .playing, isLoading: false)
        }
    }

    private var itemDurationMs: Int64 {
        guard let duration = player?.currentItem?.duration,
              duration.isNumeric && !duration.seconds.isNaN && duration.seconds > 0
        else { return 0 }
        return Int64(duration.seconds * 1000)
    }

    private func pushUpdate(
        isPlaying: Bool,
        isLoading: Bool,
        positionMs: Int64? = nil,
        error: String? = nil
    ) {
        let seconds = player?.currentTime().seconds
        let position = positionMs
            ?? ((seconds != nil && seconds!.isFinite && seconds! >= 0) ? Int64(seconds! * 1000) : 0)
        let duration = itemDurationMs
        let speed = currentSpeed
        let sleepRemainingMs = sleepRemainingSeconds > 0 ? sleepRemainingSeconds * 1000 : 0
        onPlaybackUpdate?(isPlaying, isLoading, position, duration, Float(speed), sleepRemainingMs, error)
    }

    func extractMetadata(
        filePath: String,
        fallbackTitle: String,
        completion: @escaping (_ title: String, _ author: String?, _ album: String?, _ durationMs: Int64) -> Void
    ) {
        let asset = AVURLAsset(url: URL(fileURLWithPath: filePath))
        asset.loadValuesAsynchronously(
            forKeys: ["duration", "commonMetadata"]
        ) {
            let duration = asset.duration.seconds
            let durationMs: Int64 = (duration.isFinite && duration > 0) ? Int64(duration * 1000) : 0
            var title: String?
            var artist: String?
            var album: String?
            for item in asset.commonMetadata {
                if item.commonKey == .commonKeyTitle {
                    title = item.stringValue
                } else if item.commonKey == .commonKeyArtist {
                    artist = item.stringValue
                } else if item.commonKey == .commonKeyAlbumName {
                    album = item.stringValue
                }
            }
            let resolvedTitle = (title?.isEmpty == false) ? title! : fallbackTitle
            DispatchQueue.main.async {
                completion(resolvedTitle, artist, album, durationMs)
            }
        }
    }
}