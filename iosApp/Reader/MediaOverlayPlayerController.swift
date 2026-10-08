//
//  MediaOverlayPlayerController.swift
//  Reader
//
//  Native AVPlayer-backed EPUB media overlay (publisher narration) playback.
//
//  Kotlin owns the sequencing — which clip plays next, where narration is anchored, when the run
//  ends — and drives this controller through ReaderIosBridge handlers, exactly as it drives
//  AudiobookPlayerController. The difference is the unit of playback: a clip is one sentence inside
//  a long recording, so there is no item-per-clip to advance through. `AVPlayerItem` has no
//  clipping configuration, so the controller seeks to each clip's `clipBegin` and enforces each
//  clip's end itself, advancing on a periodic observer rather than on an item transition.
//
//  The audio is not a file. `reader-epub-audio:` URIs name entries inside the EPUB's zip, served by
//  an AVAssetResourceLoader whose data requests are answered by Kotlin, which holds the archive.
//  Nothing is extracted to disk: the reference book is 124 MB, mostly audio, and a reader who opens
//  a narrated book but never presses play must not pay for the whole recording.
//
//  Everything here speaks plain Swift `Int`. Kotlin's `Int` is not one type on this boundary: a value
//  passed *into* Swift arrives as `Int32`, and one handed to a Swift *closure* arrives boxed as
//  `KotlinInt`. ContentView unwraps those at the bridge edge so this file never has to know which
//  form it was handed, and so a future third form is a one-line change there instead of a sweep here.
//

import AVFoundation
import Foundation
import MediaPlayer
import ReaderShared
import UIKit

/// One clip the controller has been asked to play, in `AVPlayer` coordinates.
struct MediaOverlayClipRequest {
    let clipIndex: Int
    let audioUri: String
    let clipBeginMs: Double
    /// The clip's declared end, or nil when it runs to the end of the media.
    let clipEndMs: Double?
}

struct MediaOverlayPlaybackSetup {
    let spineItemIndex: Int
    let title: String
    let narrator: String?
    let totalDurationMs: Double?
    let clips: [MediaOverlayClipRequest]
    let startPlaybackIndex: Int
    let playWhenReady: Bool
}

/// Serves `reader-epub-audio:` byte ranges from the EPUB archive Kotlin already has open.
///
/// One loader per clip rather than one per controller, and the entry path is fixed at construction
/// instead of parsed per request. `AVAssetResourceLoadingRequest` does not carry the URL it is for —
/// `AVAssetResourceLoadingDataRequest` has an offset and a length and nothing else — so a shared
/// loader would have to guess which clip a request belongs to. Binding it at construction is the only
/// way to be right, and it also means a stale loader cannot serve a later clip's bytes.
final class MediaOverlayResourceLoader: NSObject, AVAssetResourceLoaderDelegate {
    /// The bridge that holds the open archive. Set once by the host; the loader has no other way to
    /// reach the bytes, which are Kotlin's rather than this side's.
    weak var bridge: ReaderIosBridge?

    /// The archive entry this loader serves, decoded from its `reader-epub-audio:` URI.
    let entryPath: String?

    init(uri: String) {
        entryPath = MediaOverlayResourceLoader.entryPath(of: uri)
    }

    private var loadingRequests: [AVAssetResourceLoadingRequest] = []
    private let lock = NSLock()

    func resourceLoader(
        _ resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource loadingRequest: AVAssetResourceLoadingRequest
    ) -> Bool {
        lock.lock()
        loadingRequests.append(loadingRequest)
        let isFirst = loadingRequests.count == 1
        lock.unlock()
        if isFirst {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.processRequests()
            }
        }
        return true
    }

    func resourceLoader(
        _ resourceLoader: AVAssetResourceLoader,
        didCancel loadingRequest: AVAssetResourceLoadingRequest
    ) {
        lock.lock()
        loadingRequests.removeAll { $0 === loadingRequest }
        lock.unlock()
    }

    /// Android parity (`SharedEpubZipEntryDataSource`): requests are served one at a time and in
    /// order, because a media framework asks for monotonically increasing windows of the same
    /// stream, and answering out of order would make a clip's audio arrive after the clip that
    /// follows it.
    private func processRequests() {
        while true {
            lock.lock()
            guard !loadingRequests.isEmpty else {
                lock.unlock()
                return
            }
            let request = loadingRequests.removeFirst()
            lock.unlock()

            guard let contentRequest = request.dataRequest else {
                // A request carrying no data request is a content-information-only probe, and the
                // information request below still has to be answered for the asset to become ready.
                // Finishing is the only correct move once there is nothing to respond to, and
                // leaving it outstanding would stall readiness forever.
                if let informationRequest = request.contentInformationRequest, let entryPath {
                    fill(informationRequest, entryPath: entryPath)
                }
                request.finishLoading()
                continue
            }
            guard let entryPath, let bridge else {
                // `respond(with:)` takes bytes, not an error, so a failure is reported on the loading
                // request. Answering with no bytes would read as "a zero-length file", which is a
                // successful load of nothing.
                request.finishLoading(with: URLError(.fileDoesNotExist))
                continue
            }

            if let informationRequest = request.contentInformationRequest {
                fill(informationRequest, entryPath: entryPath)
            }

            let offset = contentRequest.currentOffset
            if contentRequest.requestsAllDataToEndOfResource {
                let declared = Int64(bridge.mediaOverlayEntryLength(entryPath: entryPath))
                let remaining = max(declared - offset, 0)
                guard remaining > 0,
                      let data = bridge.mediaOverlayReadEntry(
                        entryPath: entryPath,
                        offset: Double(offset),
                        length: remaining
                      )
                else {
                    // A zero-length answer is how a resource loader says "this is the end", which is
                    // the honest reading of an entry the archive could not produce. Failing instead
                    // would surface as a playback error on a book that merely has no audio here.
                    contentRequest.respond(with: Data())
                    request.finishLoading()
                    continue
                }
                contentRequest.respond(with: data)
                request.finishLoading()
                continue
            }

            let requested = Int64(contentRequest.requestedLength)
            guard requested > 0,
                  let data = bridge.mediaOverlayReadEntry(
                    entryPath: entryPath,
                    offset: Double(offset),
                    length: requested
                  )
            else {
                request.finishLoading(with: URLError(.fileDoesNotExist))
                continue
            }
            contentRequest.respond(with: data)
            request.finishLoading()
        }
    }

    /// Tells AVFoundation what it is about to be fed.
    ///
    /// Byte-range support is declared from the real length rather than assumed, because an asset that
    /// reports `isByteRangeAccessSupported` and then refuses a seek is the one failure this loader
    /// exists to prevent: a clip's `clipBegin` is rarely the start of the file, so every clip but
    /// the first needs a seek to land in the right place.
    private func fill(_ informationRequest: AVAssetResourceLoadingContentInformationRequest, entryPath: String) {
        let length = Int64(bridge?.mediaOverlayEntryLength(entryPath: entryPath) ?? 0)
        informationRequest.contentType = Self.contentType(of: entryPath)
        informationRequest.contentLength = length
        informationRequest.isByteRangeAccessSupported = length > 0
    }

    /// The percent-decoded entry path inside a `reader-epub-audio:` URI.
    ///
    /// Deliberately a local decode rather than the Kotlin one: the scheme and its encoding are
    /// defined by `SharedMediaOverlayAudioUri`, and this is the mirror of `entryPathOf`. The
    /// `readerMediaOverlayDiag` on mismatch is the only way a divergence would ever be visible.
    static func entryPath(of uri: String) -> String? {
        let prefix = "reader-epub-audio:///"
        guard uri.lowercased().hasPrefix(prefix) else { return nil }
        let encoded = String(uri.dropFirst(prefix.count))
        guard !encoded.isEmpty else { return nil }
        return encoded.removingPercentEncoding
    }

    private static func contentType(of entryPath: String) -> String {
        switch (entryPath as NSString).pathExtension.lowercased() {
        case "mp3": return "audio/mpeg"
        case "m4a", "mp4", "aac": return "audio/mp4"
        case "wav": return "audio/wav"
        case "ogg", "oga", "opus": return "audio/ogg"
        default: return "audio/mpeg"
        }
    }
}

class MediaOverlayPlayerController {
    /// The bridge holding the open archive, handed to each clip's resource loader.
    ///
    /// Set once by the host and read by every loader this controller makes. Weak on the loader side
    /// because the bytes are Kotlin's and the loader must not be what keeps the bridge alive.
    var bridge: ReaderIosBridge?

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var failureObserver: NSObjectProtocol?
    private var statusObserver: NSKeyValueObservation?
    private var interruptionObserver: NSObjectProtocol?
    private var routeChangeObserver: NSObjectProtocol?
    private var isLoading = false
    private var currentSpeed: Float = 1
    private var wasPlayingBeforeInterruption = false
    private var nowPlayingTitle: String = ""
    private var nowPlayingSubtitle: String?

    /// The loaded chapter's clips, and the index the engine is on. The engine owns the *decision*
    /// to advance; this is only the table it advances through.
    private var clips: [MediaOverlayClipRequest] = []
    private var currentClipIndex: Int = 0

    /// Guards against two advances from one boundary: the tick that sees the clip end and the
    /// item-ended notification can both fire for the same instant.
    private var advanceScheduled = false

    /// The loader feeding the current item, held so it outlives the asset that only weakly refers to it.
    private var activeLoader: MediaOverlayResourceLoader?

    /// Invoked on every clip advance and state change. The five values mirror Android's
    /// `AndroidSharedMediaOverlayPlayback` publishing, so the Kotlin engine's transitions are the
    /// same ones on both platforms.
    var onUpdate: ((
        _ spineItemIndex: Int,
        _ clipIndex: Int,
        _ positionMs: Double,
        _ isPlaying: Bool,
        _ isLoading: Bool,
        _ error: String?
    ) -> Void)?

    /// Called when the loaded chapter's clips have played out. Kotlin decides whether that means
    /// "the next chapter" or "the book is done".
    var onChapterFinished: ((Int) -> Void)?

    /// Called after teardown so the host can drop the session bookkeeping.
    var onPlaybackSessionEnded: (() -> Void)?

    // MARK: - Transport

    func play(_ setup: MediaOverlayPlaybackSetup) {
        stopInternal(notifyEnded: false)
        guard let first = setup.clips.indices.contains(Int(setup.startPlaybackIndex))
            ? setup.clips[Int(setup.startPlaybackIndex)]
            : setup.clips.first
        else {
            onUpdate?(-1, 0, 0, false, false, "This chapter has no narration audio")
            return
        }

        // setActive/setCategory can block on route negotiation (AudiobookPlayerController.swift:58);
        // never run them on the main thread.
        DispatchQueue.global(qos: .userInitiated).async {
            try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.allowAirPlay])
            try? AVAudioSession.sharedInstance().setActive(true)
        }

        clips = setup.clips
        currentClipIndex = first.clipIndex
        isLoading = true
        nowPlayingTitle = setup.title
        nowPlayingSubtitle = setup.narrator
        installRemoteCommands()
        installAudioSessionObservers()
        load(setup.spineItemIndex, clip: first, autoplay: setup.playWhenReady)
    }

    func pause() {
        player?.pause()
        publish(isPlaying: false)
    }

    func resume() {
        guard player != nil else { return }
        player?.playImmediately(atRate: currentSpeed)
        publish(isPlaying: true)
    }

    func setSpeed(_ speed: Float) {
        currentSpeed = speed > 0 ? speed : 1
        if player?.timeControlStatus == .playing {
            player?.rate = currentSpeed
        }
        refreshNowPlayingInfo()
    }

    /// Restarts the active clip from its own beginning.
    func restartClip() {
        guard let clip = clip(at: currentClipIndex) else { return }
        load(lastSpineItemIndex, clip: clip, autoplay: player?.timeControlStatus == .playing)
    }

    /// Moves to a clip the engine named, by its *document* clip index.
    ///
    /// Immediate, unlike an automatic advance: this arrives from the host, not from inside one of
    /// AVFoundation's own callbacks, so there is no re-entrancy to avoid — and a reader pressing
    /// "next passage" should hear it now rather than a turn later.
    func seek(toClip clipIndex: Int, spineItemIndex: Int) {
        guard let clip = clip(at: clipIndex) else { return }
        lastSpineItemIndex = spineItemIndex
        currentClipIndex = clipIndex
        load(spineItemIndex, clip: clip, autoplay: true)
    }

    func stop() {
        stopInternal(notifyEnded: true)
    }

    private var lastSpineItemIndex: Int = -1

    private func clip(at clipIndex: Int) -> MediaOverlayClipRequest? {
        clips.first { $0.clipIndex == clipIndex }
    }

    private func load(_ spineItemIndex: Int, clip: MediaOverlayClipRequest, autoplay: Bool) {
        tearDownPlayer()
        lastSpineItemIndex = spineItemIndex
        currentClipIndex = clip.clipIndex

        guard let url = URL(string: clip.audioUri) else {
            isLoading = false
            onUpdate?(spineItemIndex, clip.clipIndex, clip.clipBeginMs, false, false, "Narration audio is unavailable")
            return
        }
        guard let bridge else {
            isLoading = false
            onUpdate?(spineItemIndex, clip.clipIndex, clip.clipBeginMs, false, false, "Narration audio is unavailable")
            return
        }
        let loader = MediaOverlayResourceLoader(uri: clip.audioUri)
        loader.bridge = bridge
        guard loader.entryPath != nil else {
            isLoading = false
            onUpdate?(spineItemIndex, clip.clipIndex, clip.clipBeginMs, false, false, "Narration audio is unavailable")
            return
        }
        // Held for as long as the item is: the asset keeps only a weak reference to its loader, and a
        // loader that deallocated mid-clip would leave every later byte request unhandled.
        activeLoader = loader
        let asset = AVURLAsset(url: url)
        asset.resourceLoader.setDelegate(loader, queue: DispatchQueue(label: "reader.mediaoverlay.loader"))
        let item = AVPlayerItem(asset: asset)
        let newPlayer = AVPlayer(playerItem: item)
        player = newPlayer
        isLoading = true
        currentSpeed = playerSpeed
        installObservers(for: item)
        installTimeObserver()
        refreshNowPlayingInfo()

        // Seek before playing: the clip's `clipBegin` is rarely the start of the file, and playing
        // from zero would put a line of unrelated audio under a highlight that names this one.
        newPlayer.seek(
            to: CMTime(seconds: clip.clipBeginMs / 1000.0, preferredTimescale: 600),
            toleranceBefore: .zero,
            toleranceAfter: .zero
        ) { [weak self] _ in
            guard let self = self else { return }
            self.isLoading = false
            if autoplay {
                self.player?.playImmediately(atRate: self.playerSpeed)
            }
            self.publish(isPlaying: autoplay)
        }
    }

    private var playerSpeed: Float { currentSpeed > 0 ? currentSpeed : 1 }

    private func tearDownPlayer() {
        if let timeObserver {
            player?.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        endObserver = nil
        if let failureObserver {
            NotificationCenter.default.removeObserver(failureObserver)
        }
        failureObserver = nil
        statusObserver?.invalidate()
        statusObserver = nil
        player = nil
        // Dropped with the player: the loader belongs to one clip's asset, and a stale one answering
        // a later request would serve the previous clip's bytes.
        activeLoader = nil
    }

    private func stopInternal(notifyEnded: Bool) {
        // A stop can occur between periodic observer ticks; publish synchronously while the player
        // still exists so the host sees the final state rather than a stale one.
        if player != nil {
            player?.pause()
            publish(isPlaying: false)
        }
        tearDownPlayer()
        if let interruptionObserver {
            NotificationCenter.default.removeObserver(interruptionObserver)
        }
        interruptionObserver = nil
        if let routeChangeObserver {
            NotificationCenter.default.removeObserver(routeChangeObserver)
        }
        routeChangeObserver = nil
        clips = []
        currentClipIndex = 0
        lastSpineItemIndex = -1
        isLoading = false
        wasPlayingBeforeInterruption = false
        removeRemoteCommands()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        deactivateAudioSession()
        if notifyEnded {
            onPlaybackSessionEnded?()
        }
    }

    // MARK: - Observers

    /// How often the clip bound is checked, and how often the position is published.
    ///
    /// A clip is a sentence, so this is the granularity at which a boundary can be overshot by
    /// anything audible: at 250 ms the worst overshoot is a quarter second of the *next* line under
    /// this line's highlight, against a clip typically two seconds long. Finer polling would cost
    /// main-thread work per clip for no perceptible gain, and coarser would make the boundary itself
    /// the visible artefact.
    private static let tickInterval = CMTime(seconds: 0.25, preferredTimescale: 600)

    private func installTimeObserver() {
        timeObserver = player?.addPeriodicTimeObserver(
            forInterval: Self.tickInterval,
            queue: .main
        ) { [weak self] time in
            guard let self = self else { return }
            let positionMs = time.seconds * 1000.0
            let isPlaying = self.player?.timeControlStatus == .playing
            self.publish(isPlaying: isPlaying, positionMs: positionMs)
            self.enforceClipEnd(atMs: positionMs)
        }
    }

    /// The clip bound `AVPlayerItem` cannot express.
    ///
    /// This is the counterpart to Android handing ExoPlayer a `ClippingConfiguration`: with no
    /// clipping, a clip would otherwise run past its `clipEnd` and the highlight would keep moving
    /// over text the narrator has already left.
    private func enforceClipEnd(atMs positionMs: Double) {
        guard let clip = clip(at: currentClipIndex), let end = clip.clipEndMs, end > 0 else { return }
        guard positionMs >= end else { return }
        advanceAfterCurrentClip()
    }

    /// Moves to the next clip in the loaded chapter, or reports the chapter finished.
    ///
    /// Deferred to the next main-loop turn rather than run inside the caller. The caller is usually the
    /// periodic observer or the item-ended notification — i.e. AVFoundation is dispatching *to* this
    /// controller — and replacing the player from inside one of its own callbacks is the re-entrancy
    /// Android's `AndroidSharedMediaOverlayPlayback.notifyChapterFinished` exists to avoid. The failure
    /// is not a crash: the next chapter loads, reports its first clip, and then never plays a note.
    /// Android sees the same thing through ExoPlayer's listener dispatch, so both platforms defer.
    ///
    /// A chapter that ends and waits a turn is inaudible, so the gap concern that would argue for
    /// doing it synchronously does not apply at a 250 ms tick.
    private func advanceAfterCurrentClip() {
        guard !advanceScheduled else { return }
        advanceScheduled = true
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.advanceScheduled = false
            self.performAdvance()
        }
    }

    private func performAdvance() {
        guard let index = clips.firstIndex(where: { $0.clipIndex == currentClipIndex }) else {
            finishChapter()
            return
        }
        let nextIndex = index + 1
        guard clips.indices.contains(nextIndex) else {
            finishChapter()
            return
        }
        let next = clips[nextIndex]
        let wasPlaying = player?.timeControlStatus == .playing
        load(lastSpineItemIndex, clip: next, autoplay: wasPlaying)
    }

    private func finishChapter() {
        let finished = lastSpineItemIndex
        tearDownPlayer()
        clips = []
        currentClipIndex = 0
        lastSpineItemIndex = -1
        isLoading = false
        onUpdate?(finished, 0, 0, false, false, nil)
        onChapterFinished?(finished)
    }

    private func installObservers(for item: AVPlayerItem) {
        let center = NotificationCenter.default
        endObserver = center.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            // The end of the *media*, not of the clip: a clip whose `clipEnd` is null runs to here,
            // and one that has an end is normally advanced by the periodic observer first.
            self?.advanceAfterCurrentClip()
        }
        failureObserver = center.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] notification in
            let error = (notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error)?
                .localizedDescription
            self?.isLoading = false
            self?.publish(isPlaying: false, error: error ?? "Could not play this narration")
        }
        statusObserver = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            guard let self = self else { return }
            if item.status == .failed {
                self.isLoading = false
                self.publish(
                    isPlaying: false,
                    error: item.error?.localizedDescription ?? "Could not play this narration"
                )
            }
        }
    }

    /// AVPlayer does not mirror Android's audio-focus/noisy behaviour on its own. Same contract as
    /// the audiobook controller: pause on an interruption or a headphone removal, resume only when
    /// iOS says the session may and playback was active before.
    private func installAudioSessionObservers() {
        let center = NotificationCenter.default
        interruptionObserver = center.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            guard let self = self else { return }
            let typeRaw = (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.uintValue ?? 0
            if typeRaw == AVAudioSession.InterruptionType.began.rawValue {
                self.wasPlayingBeforeInterruption = self.player?.timeControlStatus == .playing
                if self.wasPlayingBeforeInterruption {
                    self.player?.pause()
                }
                self.publish(isPlaying: false)
            } else if typeRaw == AVAudioSession.InterruptionType.ended.rawValue {
                let optionsRaw = (notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.uintValue ?? 0
                let shouldResume = AVAudioSession.InterruptionOptions(rawValue: optionsRaw).contains(.shouldResume)
                if self.wasPlayingBeforeInterruption && shouldResume {
                    DispatchQueue.global(qos: .userInitiated).async {
                        try? AVAudioSession.sharedInstance().setActive(true)
                    }
                    self.player?.playImmediately(atRate: self.playerSpeed)
                    self.publish(isPlaying: true)
                } else {
                    self.publish(isPlaying: false)
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
            let reasonRaw = (notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? NSNumber)?.uintValue ?? 0
            if reasonRaw == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue,
               self.player?.timeControlStatus == .playing {
                self.player?.pause()
                self.publish(isPlaying: false)
            }
        }
    }

    private func deactivateAudioSession() {
        DispatchQueue.global(qos: .userInitiated).async {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
    }

    private func publish(isPlaying: Bool, positionMs: Double? = nil, error: String? = nil) {
        let position = positionMs ?? currentPositionMs
        onUpdate?(lastSpineItemIndex, currentClipIndex, position, isPlaying, isLoading, error)
        if isPlaying != (player?.timeControlStatus == .playing) || error != nil {
            refreshNowPlayingInfo()
        }
    }

    private var currentPositionMs: Double {
        guard let time = player?.currentTime() else { return 0 }
        let seconds = time.seconds
        return seconds.isFinite && seconds >= 0 ? seconds * 1000.0 : 0
    }

    // MARK: - Now Playing / remote commands

    private func installRemoteCommands() {
        let commands = MPRemoteCommandCenter.shared()
        commands.playCommand.addTarget { [weak self] _ in
            self?.resume()
            return .success
        }
        commands.pauseCommand.addTarget { [weak self] _ in
            self?.pause()
            return .success
        }
        commands.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            if self.player?.timeControlStatus == .playing {
                self.pause()
            } else {
                self.resume()
            }
            return .success
        }
        // Narration's unit is a sentence, so the lock screen skips a clip rather than a fixed
        // interval. Android's notification shows the same previous/next pair over the same clips.
        commands.skipForwardCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            self.advanceAfterCurrentClip()
            return .success
        }
        commands.skipBackwardCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            guard let index = self.clips.firstIndex(where: { $0.clipIndex == self.currentClipIndex }),
                  index > 0
            else { return .commandFailed }
            self.seek(
                toClip: self.clips[index - 1].clipIndex,
                spineItemIndex: self.lastSpineItemIndex
            )
            return .success
        }
    }

    private func removeRemoteCommands() {
        let commands = MPRemoteCommandCenter.shared()
        for command in [
            commands.playCommand,
            commands.pauseCommand,
            commands.togglePlayPauseCommand,
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
        // Duration and elapsed are deliberately absent. The recording is one long file reached
        // through a custom scheme, so AVFoundation cannot report either, and publishing a wrong
        // number would put a scrubber on the lock screen that seeks to the wrong place — worse than
        // no scrubber at all. Transport works through skip commands instead, which is also what the
        // unit of playback wants: a clip, not a second.
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: nowPlayingTitle,
            MPMediaItemPropertyAlbumTitle: "Narration",
            MPMediaItemPropertyMediaType: NSNumber(value: MPMediaType.anyAudio.rawValue),
            MPNowPlayingInfoPropertyPlaybackRate: (player?.timeControlStatus == .playing) ? Double(playerSpeed) : 0.0,
        ]
        if let subtitle = nowPlayingSubtitle, !subtitle.isEmpty {
            info[MPMediaItemPropertyArtist] = subtitle
        }
        center.nowPlayingInfo = info
    }
}