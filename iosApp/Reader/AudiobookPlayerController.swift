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
import MediaPlayer

class AudiobookPlayerController {
    private var player: AVPlayer?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var failureObserver: NSObjectProtocol?
    private var statusObserver: NSKeyValueObservation?
    private var sleepTickTimer: Timer?
    private var interruptionObserver: NSObjectProtocol?
    private var routeChangeObserver: NSObjectProtocol?
    private var sleepRemainingSeconds: Int64 = 0
    private var pendingSeekMs: Double?
    private var isLoading = false
    private var currentSpeed: Float = 1
    private var nowPlayingTitle: String = ""
    private var nowPlayingSubtitle: String?
    private var wasPlayingBeforeInterruption = false

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
    /// Called after a sleep-timer expiry has published its final position and
    /// the native player has been torn down. The Kotlin host flushes and clears
    /// its matching session snapshot.
    var onPlaybackSessionEnded: (() -> Void)?

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
        nowPlayingTitle = url.lastPathComponent
        nowPlayingSubtitle = nil
        installRemoteCommands()
        installObservers(for: item)
        installAudioSessionObservers()
        refreshNowPlayingInfo()
        // Embedded metadata (title/artist/album) replaces the filename once known,
        // matching the library card presentation.
        extractMetadata(filePath: filePath, fallbackTitle: url.lastPathComponent) { [weak self] title, author, album, _ in
            self?.nowPlayingTitle = title
            self?.nowPlayingSubtitle = author ?? album
            self?.refreshNowPlayingInfo()
        }
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
        sleepTickTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] timer in
            guard let self = self else { return }
            guard self.sleepRemainingSeconds > 0 else {
                timer.invalidate()
                self.sleepTickTimer = nil
                return
            }

            // Match Android's shared timer contract: paused playback does not
            // consume sleep time. A single repeating timer avoids expiring an
            // absolute wall-clock timer while AVPlayer is paused/backgrounded.
            guard self.player?.timeControlStatus == .playing else {
                self.pushUpdate(isPlaying: false, isLoading: false)
                return
            }

            self.sleepRemainingSeconds -= 1
            if self.sleepRemainingSeconds <= 0 {
                timer.invalidate()
                self.sleepTickTimer = nil
                self.expireSleepTimer()
            } else {
                self.pushUpdate(isPlaying: true, isLoading: false)
            }
        }
        pushUpdate(isPlaying: player?.timeControlStatus == .playing, isLoading: false)
    }

    func cancelSleepTimer() {
        let hadActiveTimer = sleepRemainingSeconds > 0
        invalidateSleepTimer()
        if hadActiveTimer {
            pushUpdate(isPlaying: player?.timeControlStatus == .playing, isLoading: false)
        }
    }

    private func invalidateSleepTimer() {
        sleepTickTimer?.invalidate()
        sleepTickTimer = nil
        sleepRemainingSeconds = 0
    }

    private func expireSleepTimer() {
        sleepRemainingSeconds = 0
        player?.pause()
        // Publish the final AVPlayer position before deactivating audio so the
        // Kotlin host can persist the same boundary Android saves on expiry.
        pushUpdate(isPlaying: false, isLoading: false)
        stopInternal(publishFinalPosition: false)
        onPlaybackSessionEnded?()
    }

    func stop() {
        stopInternal()
    }

    private func stopInternal(publishFinalPosition: Bool = true) {
        invalidateSleepTimer()
        // A stop can occur between periodic observer ticks. Publish the
        // current position synchronously while AVPlayer still exists so the
        // host can persist the exact final position.
        if publishFinalPosition, player != nil {
            player?.pause()
            pushUpdate(isPlaying: false, isLoading: false)
        }
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
        if let interruptionObserver = interruptionObserver {
            NotificationCenter.default.removeObserver(interruptionObserver)
        }
        interruptionObserver = nil
        if let routeChangeObserver = routeChangeObserver {
            NotificationCenter.default.removeObserver(routeChangeObserver)
        }
        routeChangeObserver = nil
        player = nil
        isLoading = false
        pendingSeekMs = nil
        wasPlayingBeforeInterruption = false
        removeRemoteCommands()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        deactivateAudioSession()
    }

    // MARK: - Now Playing / remote commands (Android's media-notification contract)

    private func installRemoteCommands() {
        let commands = MPRemoteCommandCenter.shared()
        commands.playCommand.addTarget(handler:) { [weak self] _ in
            self?.resume(speed: self?.currentSpeed ?? 1)
            return .success
        }
        commands.pauseCommand.addTarget(handler:) { [weak self] _ in
            self?.pause()
            return .success
        }
        commands.togglePlayPauseCommand.addTarget(handler:) { [weak self] _ in
            guard let self = self else { return .commandFailed }
            if self.player?.timeControlStatus == .playing {
                self.pause()
            } else {
                self.resume(speed: self.currentSpeed)
            }
            return .success
        }
        commands.changePlaybackPositionCommand.addTarget(handler:) { [weak self] event in
            guard let positionEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            self?.seek(to: positionEvent.positionTime * 1000)
            return .success
        }
        for (command, interval) in [
            (commands.skipForwardCommand, 15.0),
            (commands.skipBackwardCommand, -15.0),
        ] {
            command.preferredIntervals = [NSNumber(value: abs(interval))]
            command.addTarget(handler:) { [weak self] _ in
                guard let self = self, let player = self.player else { return .commandFailed }
                let current = player.currentTime().seconds
                let target = max(current + interval, 0)
                self.seek(to: target * 1000)
                return .success
            }
        }
    }

    private func removeRemoteCommands() {
        let commands = MPRemoteCommandCenter.shared()
        for command in [
            commands.playCommand,
            commands.pauseCommand,
            commands.togglePlayPauseCommand,
            commands.changePlaybackPositionCommand,
            commands.skipForwardCommand,
            commands.skipBackwardCommand,
        ] {
            command.removeTarget(nil)
        }
    }

    private func refreshNowPlayingInfo() {
        let center = MPNowPlayingInfoCenter.default()
        guard player != nil else {
            center.nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: nowPlayingTitle,
            MPNowPlayingInfoPropertyPlaybackRate: (player?.timeControlStatus == .playing) ? Double(currentSpeed) : 0.0,
        ]
        if let subtitle = nowPlayingSubtitle, !subtitle.isEmpty {
            info[MPMediaItemPropertyArtist] = subtitle
        }
        let durationSeconds = player?.currentItem?.duration.seconds ?? 0
        if durationSeconds.isFinite && durationSeconds > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = durationSeconds
        }
        let elapsedSeconds = player?.currentTime().seconds ?? 0
        if elapsedSeconds.isFinite && elapsedSeconds >= 0 {
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsedSeconds
        }
        center.nowPlayingInfo = info
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

    // AVPlayer does not automatically mirror Android's audio-focus/noisy
    // behavior. Pause on phone-call/audio interruptions and headphone removal,
    // then resume only when iOS explicitly says the interrupted session may
    // resume and playback was active before the interruption.
    private func installAudioSessionObservers() {
        let center = NotificationCenter.default
        interruptionObserver = center.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            guard let self = self else { return }
            let typeRawValue = (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.uintValue ?? 0
            if typeRawValue == AVAudioSession.InterruptionType.began.rawValue {
                self.wasPlayingBeforeInterruption = self.player?.timeControlStatus == .playing
                if self.wasPlayingBeforeInterruption {
                    self.player?.pause()
                }
                self.pushUpdate(isPlaying: false, isLoading: false)
            } else if typeRawValue == AVAudioSession.InterruptionType.ended.rawValue {
                let optionsRawValue = (notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.uintValue ?? 0
                let shouldResume = AVAudioSession.InterruptionOptions(rawValue: optionsRawValue)
                    .contains(.shouldResume)
                if self.wasPlayingBeforeInterruption && shouldResume {
                    try? AVAudioSession.sharedInstance().setActive(true)
                    self.player?.play()
                    self.pushUpdate(isPlaying: true, isLoading: false)
                } else {
                    self.pushUpdate(isPlaying: false, isLoading: false)
                }
                self.wasPlayingBeforeInterruption = false
            }
        }
        routeChangeObserver = center.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            guard let self = self else { return }
            let reasonRawValue = (notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? NSNumber)?.uintValue ?? 0
            guard reasonRawValue == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue else {
                return
            }
            if self.player?.timeControlStatus == .playing {
                self.player?.pause()
                self.pushUpdate(isPlaying: false, isLoading: false)
            }
        }
    }

    private func deactivateAudioSession() {
        try? AVAudioSession.sharedInstance().setActive(
            false,
            options: [.notifyOthersOnDeactivation]
        )
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
        refreshNowPlayingInfo()
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
