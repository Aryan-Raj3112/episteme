# iOS ↔ Android parity audit

Android is the behavioral benchmark. Shared code should own portable rules; iOS
should provide only the native storage, authentication, purchase, and system
integration boundaries.

## Priority order

| Area | Current iOS state | Android benchmark gap | Priority |
|---|---|---|---|
| Account and entitlement gates | Apple and Google Firebase auth, StoreKit Pro and credits | Provider linking still needs trusted server-side merge | P0 |
| Cloud sync | Android-compatible Firestore book metadata, Drive content transfer, reading-state merge, and deletion tombstones are implemented | Shelves, PDF sidecars, retry, and device management remain; further work is paused until iOS auth is complete | Paused |
| EPUB/text reader | Shared reader covers core layout, navigation, bookmarks, highlights and settings | Audit TTS lifecycle, search edge cases, external dictionary, image actions, chapter transitions and Android pagination rules | P0 |
| PDF reader | Shared PDF renderer, navigation, selection and annotations exist | Audit reflow/OCR, embedded annotations, rich text, sidecars, link behavior, spread/zoom and one-hand interactions | P0 |
| Library | Shared shelves, tags, smart collections, filters, sorting and local folder projection | Audit metadata editing, replacement, deletion tombstones, duplicate identity and folder rescans against Android | P1 |
| Settings | Shared settings hierarchy with platform capability gating; iOS account/sync controls now connected | Global voice configuration, capture protection and device management are absent | P1 |
| AI and cloud TTS | Not connected on iOS | Android paid/BYOK models, reader AI context, credit accounting and cloud TTS | P1, paid work currently deferred |
| Import and formats | Native picker plus shared EPUB/MOBI/comic/document loaders | Audit Android metadata extraction, strict filtering, unsupported/corrupt/password flows and replacement behavior | P1 |
| Home/navigation UI | Shared mobile shell and screens | Validate spacing, menus, contextual actions, empty states, refresh semantics and tablet layouts visually | P2 |
| Platform polish | Native file sharing, orientation, brightness and system UI hooks | Background work, screen-capture policy, notifications, deep links and restoration remain uneven | P2 |

## Cloud-sync invariants

- Sync requires an authenticated Pro account.
- Sync additionally requires a linked Google provider and `drive.appdata`
  permission.
- Apple-authenticated Pro accounts remain eligible for non-sync Pro features.
- Newer `readingPositionModifiedTimestamp` wins for portable reading state.
- A remote-only book must not appear until its content is locally available.
- Device-local paths, folder membership and file timestamps must never be
  overwritten by another device's path values.

## Implemented in the current parity pass

- Shared account/Pro eligibility rules.
- Account actions exposed in iOS Settings.
- iOS drawer and Settings sync gates match Android's Pro requirement.
- Firestore book documents use Android's metadata contract.
- Android-compatible `<bookId>.<extension>` content upload and atomic download.
- Merge preserves local file identity and ignores unopenable remote-only books.
- Timestamped book deletion tombstones prevent deleted books from reappearing;
  importing a newer replacement intentionally resurrects the book.
- Home refresh triggers cloud sync when enabled; reader close uploads changed
  progress.
- The iOS strict-file-filter setting now constrains the native document picker
  to the readable extension set from shared file capabilities.
- Native iOS imports and folder scans now stream SHA-256 over copied content and
  use Android's content hash as the book ID. Re-importing identical content from
  another location opens the existing book and removes the redundant managed
  copy instead of creating a second library record.
- Existing path-identified iOS imports are hash-backfilled and migrated without
  losing selections, pins, tabs, shelf membership, or reading metadata.
- User metadata, display-name, cover, and tag edits now advance a dedicated
  metadata clock without changing recency or reading-position time. Original
  EPUB values are retained for restore, and newer remote metadata merges
  independently from reading progress.
- Book availability is now portable state. iOS validates persisted paths at
  startup, dims and marks unavailable cards like Android, and an unavailable
  card requests a Google-gated cloud content download before opening. Failed
  downloads remain unavailable instead of entering a broken reader session.
- Managed iOS imports are removed from app storage when deleted from the
  library, instead of leaving orphaned files behind.
- Deleting a folder-managed book now warns that the action is permanent and
  deletes the matching security-scoped source file by validated relative path,
  preventing the next folder scan from resurrecting it.
- PDF page/progress changes now advance the shared reading-position timestamp;
  no-op state callbacks do not create false newer updates.
- iOS Settings no longer advertises Android-only book/reflow cache maintenance
  or a global TTS configuration screen that has no iOS handler. Reader-local
  voice and speech controls remain available where they work.
- The deferred paid AI settings row is hidden from the iOS drawer instead of
  opening a placeholder message.
- EPUB metadata edits on iOS now follow Android's file-level behavior: the
  original archive is backed up once, title/author/summary/series and cover
  changes are written into a validated temporary EPUB, and the source is
  replaced atomically. Restoring original metadata also restores the embedded
  cover from that backup. Archive work runs off the UI thread.
- iOS folder refresh now feeds relative path, size, and modification time into
  the same `LocalFolderSyncEngine` used for Android behavior. Folder identities
  are path-stable rather than content hashes, legacy hash IDs migrate with
  selections/pins/progress intact, missing files are removed, and changed
  bytes invalidate extracted cover/metadata without discarding reading state.
- Native folder mirroring is staged before replacing the last good managed
  copy. A failed bookmark/provider scan keeps the existing library and files
  instead of presenting an empty folder. EPUB edits to a folder-managed copy
  are also atomically written back through the security-scoped source bookmark,
  so the next refresh does not undo them.
- External “open without adding” requests now use an isolated reader session
  like Android: they do not deduplicate into or mutate a stored library book,
  never enter recents/shelves/tabs, do not trigger cloud sync, and delete their
  managed temporary copy when the reader closes. Reader close also consistently
  restores brightness and keep-screen-on state for PDF and reflowable formats.
- The “Use PDF filenames” setting now affects iOS home cards, library rows,
  active tabs, PDF reader title, and PDF read-aloud title. Previously iOS
  persisted and displayed the toggle while continuing to show embedded PDF
  titles.
- iOS PDF appearance defaults now reach the active reader instead of being
  settings-only state: theme and texture opacity, system-UI mode, page gap,
  page-number overlay, first-page spread behavior, and right-to-left pagination
  initialize from the saved defaults. Changes made in the PDF reader update
  those defaults, and RTL mode reverses both pager direction and edge taps.
- PDF sessions now reopen with the saved single/two-page spread and
  keep-screen-on preference. As on Android, each PDF session initially hides
  reader chrome for a distraction-free page; a tap reveals the controls.
- Keep-screen-on is now one shared reader preference across PDF and reflowable
  readers, matching Android rather than resetting when formats change.
- iOS EPUB crash-recovery snapshots are now merged by the reading-position
  clock. A stale session can no longer roll back progress, bookmarks,
  highlights, or newer title/author edits; legacy unclocked recovery is accepted
  only when the library has no reading state to preserve.
- Initial/no-op EPUB state callbacks no longer create a false newer
  reading-position timestamp. Real session changes merge onto the latest active
  book, so an asynchronous callback cannot undo another reader update.
- EPUB metadata discovered while opening a book now only backfills blank
  title/author fields. It cannot overwrite user-edited metadata, reading
  progress, bookmarks, highlights, or their independent merge clocks.
- Vertical EPUB read-aloud now follows Android's navigation contract. Manual
  chapter/search/TOC movement temporarily detaches the page without stopping
  speech or snapping back; playback rejoins when the next speech chunk begins,
  and the TTS controls can explicitly locate the current chunk immediately.
- EPUB search now uses Android's case-insensitive word-start matching rule and
  retains the exact chapter source offset for every result. Paginated iOS
  navigation resolves that offset to the matching page instead of leaving the
  reader on its previous page; vertical mode keeps exact chunk/occurrence DOM
  highlighting.
- iOS scene lifecycle now reaches the active EPUB reader. Moving the app out of
  the foreground forces an immediate portable position/session snapshot rather
  than risking the normal debounce losing the last scroll, and returning to the
  app re-locates active speech unless the reader was intentionally detached.

## Deferred cloud slice

Per project direction, stop expanding sync until iOS authentication is fully
configured. When resumed:

1. Sync shelf records/references using stable IDs and per-record timestamps.
2. Add EPUB and PDF annotation sidecars, then retry/diagnostic states.
3. Add device registration/management after the authentication contract is ready.

## Active non-auth parity slice

1. Runtime-audit home, library, settings, EPUB, and PDF behavior on the iOS
   simulator.
2. Fix reader interaction and lifecycle differences before visual-only polish.
3. Match import filtering, duplicate identity, folder refresh, metadata editing,
   and replacement behavior to Android.

Android does not expose user-directed replacement of a standalone managed
import: changed bytes receive a new SHA identity, while identical bytes resolve
to the existing library record. iOS should retain that behavior rather than
inventing a platform-only replacement workflow.
