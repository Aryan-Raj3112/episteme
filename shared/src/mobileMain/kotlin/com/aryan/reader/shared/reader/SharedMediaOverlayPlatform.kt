package com.aryan.reader.shared.reader

import androidx.compose.runtime.Composable

/**
 * The platform seam media overlays need from the archive, and nothing else.
 *
 * Everything a publisher's narration depends on beyond *reading a SMIL body out of the book* is
 * deliberately **not** a shared seam: the Android engine is a `MediaSessionService` living next to
 * the audiobook service in the app module (that is where the plan puts it, and where the manifest
 * entry and notification provider already are), and iOS has no engine yet — only the null-safe
 * [IosSharedMediaOverlayPlaybackHolder] holder an engine will install into later. An expect for the
 * engine here would have to be satisfied from `androidMain`, which cannot see the app module —
 * so the seam would either not compile or force the service into `shared`, away from its manifest.
 *
 * What genuinely is shared: the index, the parser, the clock grammar, the playback plan, the
 * projection, and the band. A platform engine implements `SharedMediaOverlayPlayback` and the rest
 * of the feature is shared code either way.
 *
 * A null reader means "this platform cannot read the archive", which is a legitimate answer rather
 * than an error: the reader must still open a narrated book. `RS §9` requires exactly that —
 * ignoring overlays is a valid reader behaviour, never a failure to open. It is also not a
 * placeholder to be filled in later at the call site; a screen that assumed an archive existed
 * would offer a button that does nothing.
 */

/**
 * Archive access for SMIL bodies, as a `path -> text` reader, or null when unavailable.
 *
 * Null rather than a throwing reader because a book whose archive cannot be read must degrade to
 * "this book does not narrate itself" — which is already what a miss means to every consumer of
 * [SharedMediaOverlayDocumentCache].
 *
 * The returned lambda is only valid for as long as the composable is alive; a caller that caches
 * one is caching an open archive, which is exactly what [SharedMediaOverlayDocumentCache] exists
 * to bound instead. Closing on disposal is the platform actual's job, which is why this is a
 * composable rather than a plain function: an open `ZipFile` must not outlive the reader screen
 * that opened it.
 */
@Composable
expect fun rememberSharedMediaOverlaySmilReader(bookPath: String): ((String) -> String?)?
