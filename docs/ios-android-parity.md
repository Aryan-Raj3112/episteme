# iOS ↔ Android parity audit

> Historical implementation log. The current authoritative gap audit and implementation roadmap is
> [`ios-android-parity-audit-plan.md`](ios-android-parity-audit-plan.md).

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
- EPUB navigation now uses the same bounded portable jump history as
  Android. Search results, TOC entries, bookmarks, annotations, book images, and
  internal links record both origin and destination; visible reader controls
  expose chapter-labelled back, forward, and clear actions in vertical and
  paginated modes, with history preserved when switching layouts.
- The EPUB drawer now removes legacy duplicate bookmarks by portable location
  before sorting them in reading order. Annotation rows show Android-equivalent
  chapter and highlight-color context in addition to their text and optional
  note, while retaining the full color/style/note/delete editor.
- The EPUB bookmark icon and toggle now use Android's location rules: exact
  locator, the same text block within 160 characters, or the visible paginated
  page. Removing a bookmark also clears legacy duplicates matching that active
  location, preventing duplicate creation and a falsely active icon after a
  small scroll.
- PDF overflow actions no longer collapse into the old current-page placeholder.
  Share opens the native activity sheet, Save Copy opens the Files export
  picker without moving the managed library file, and Print opens the native
  print controller after confirming that iOS can print the PDF. The remaining
  bridge-dependent menu items now report their own unavailable feature instead
  of misleadingly behaving like a file action.
- PDF File Information now opens the same full-screen shared metadata view used
  by the library: title, author, series, format, size, reading progress, file
  path, source folder, summary, and tags. PDF display-name/tag edits use the
  shared Android metadata mutation contract, preserving file identity, path,
  reading state, and independent metadata timestamps.
- PDF Brightness and Screen Orientation now open real shared controls instead
  of bridge placeholders. Both PDF and EPUB use Android's system/custom
  brightness contract with 1% rounding, 1–100% bounds, one-percent step
  buttons, and preservation of the last custom level while system brightness is
  active. Orientation follows the same persisted follow-system, portrait, and
  landscape modes and resets the platform override when a reader closes.
- PDF toolbar customization now uses a shared mirror of Android's PDF tool
  catalog, defaults, hidden-tool migration state, order, and top/bottom
  placement rules. iOS persists those preferences, renders both scrollable bars
  in the chosen order, keeps hidden toolbar actions reachable from the overflow
  menu, and applies visibility to supported overflow actions. Paid or
  unimplemented iOS tools are omitted from the availability set instead of
  appearing as inert controls.
- PDF Voice Settings and Word Replacements now open the same shared controls as
  EPUB. Voice, rate, and pitch update the active local speech controller, while
  global and per-book replacement changes flow through the shared library state
  and persist across reader formats. Both entries participate independently in
  PDF toolbar visibility preferences.
- PDF themes now use the shared Android-equivalent theme editor instead of the
  iOS-only preset grid with an inert custom-theme button. Saved custom themes
  can be created, edited, deleted, selected, persisted, and resolved by the
  active PDF renderer; invalid or deleted IDs safely fall back to No Theme.
  The old local-only Preserve Image Colors switch was removed because the iOS
  renderer cannot yet exclude PDF image regions from its page-wide color
  filter.
- PDF text selection now exposes Android's free lookup action set: Define,
  Translate, and Search appear only for selections up to 2,000 characters.
  Define uses the native iOS reference library, matching EPUB, while Translate
  and Search use the shared external-lookup routes instead of separate PDF-only
  URL construction. Saved-highlight editing exposes the same bounded lookup
  actions plus Read aloud, which resumes from the highlight's stored page
  character offset.
- External PDF links no longer launch immediately on iOS. Like Android, tapping
  one now shows the full destination and offers Visit, Copy, and Cancel;
  internal PDF destinations continue to navigate directly and enter the shared
  jump history.
- iOS Settings now uses Android's explicit dialogs instead of cycling values
  on tap. Recent books offers 0/10/20/50/100, strict file filtering requires
  confirmation when enabled, and sign-out is confirmed from Settings, Account,
  and the navigation drawer. External-file behavior now uses Android's
  ASK/KEEP/DELETE/TEMPORARY
  contract; legacy iOS COPY and 12/24 limits migrate to their nearest Android
  equivalents, and the native open router honors all four modes.
- Manual shelf detail no longer traps iOS in an invisible selection state.
  Long-press now opens Android-equivalent contextual controls; subsequent taps
  extend the selection, and users can clear, tag, inspect, save, share, export
  annotations, or remove selected books from that shelf. Removing shelf
  membership preserves the library books and uses a shared, tested mutation
  instead of the library-deletion path.
- Home and Library now use Android's multi-shelf chooser for the contextual
  Add to shelf action. Users can add the selected books to one or more existing
  manual shelves or continue into New shelf; shared mutation logic ignores
  folders and immutable shelves, deduplicates existing membership, and clears
  selection after the operation.
- Manual shelf detail now includes Android's dedicated Add books mode instead
  of sending iOS users back to the Library selection menu. It supports the same
  Unshelved/All books sources, sort choices, tap multi-selection, selected-count
  confirmation, and source-specific empty states. Candidate filtering is shared
  with Android's library projector and membership updates use the same
  deduplicating shelf mutation.
- Shelf detail now carries Android's local management header on iOS: book count,
  all shared sort orders, shelf-scoped title/author/tag search with clear and
  no-results states, plus rename and confirmed delete for mutable manual
  shelves. Search state resets when leaving the shelf, and management actions
  remain hidden for synthetic, smart, tag, series, and folder shelves.
- Folder shelf detail no longer flattens every descendant file on iOS. It now
  mirrors Android's hierarchy: child folders and directly contained files are
  separate sections, child rows navigate deeper, Back returns to the parent
  folder before leaving Library, and folder search matches both child names and
  descendant book metadata. Shared projection tests assert direct membership,
  child IDs, and parent IDs.
- Home contextual Remove now matches Android's non-destructive Recents
  operation. iOS confirms the action, clears only `isRecent` and the contextual
  selection, advances the portable metadata clock, and preserves the library
  record, managed file path, shelf membership, open reader tabs, and Home pin.
  The previous iOS path permanently deleted managed and folder-backed files.
- Shelf contextual selection and deletion now enforce Android's mutable-manual
  boundary. Smart, series, folder, tag, and reserved Unshelved rows cannot enter
  shelf selection; rename/delete reject those IDs again in shared mutation
  logic. Deleting a manual shelf removes only that shelf and preserves every
  library record, source-folder link, and file path. Synced folders remain
  removable only through their dedicated folder-sync action.
- Shelf multi-selection now shows Android's actual action set on iOS: close,
  selected count, and delete. The visible but inert Select all and Pin buttons,
  plus the non-benchmark contextual Rename shortcut, were removed; rename
  remains available inside an individual manual shelf. Folder cards also show
  the real localized `MMM d, h:mm a` last-scan time instead of the literal word
  “Updated.”
- Folder linking now enforces Android's ten-folder cap through shared tested
  policy, hiding Add Folder once the unique linked-folder limit is reached.
  Disabling an enabled folder now requires confirmation instead of changing
  immediately; iOS explicitly retains its managed reading copies because it
  does not create Android's removable `.syncdata` directory.
- Home and Library contextual pinning now use Android's batch rule instead of
  independently inverting each selected book. A mixed or wholly unpinned
  selection becomes fully pinned; a selection that is already entirely pinned
  becomes fully unpinned. Both paths clear contextual selection while
  preserving pins outside the selected set.
- Contextual tagging now uses Android's searchable tri-state tag sheet on iOS
  instead of a comma-separated replacement field. Mixed tags assign to the
  whole selection, fully assigned tags are removed from the whole selection,
  unrelated tags are preserved, new tags can be created in place, and global
  tag deletion is confirmed and removed from every projected book surface.
- The iOS Library filter now matches Android's transactional bottom sheet:
  changes remain a local draft until Apply, dismissing cancels them, Clear all
  clears the draft, source filters include in-app storage and every synced
  folder, file-type choices are restricted to readable formats, and tag chips
  show their configured colors.
- The iOS Home app bar now mirrors Android's quick-action structure: Settings,
  app theme, recent-file limit, and a real overflow menu for About, multi-tab
  reading, external-file behavior, strict filtering, PDF filename display, and
  language. Checked toggles reflect current state, language returns to its
  launch context, and folder/cloud refresh moved from an extra toolbar icon to
  Android's conditional pull-to-refresh gesture.
- Home and Library Select all now use Android's visible-set toggle. The first
  action replaces stale or partial selection with exactly the currently visible
  recent/filtered library IDs; invoking it when that full visible set is
  already selected clears contextual mode. Duplicate or blank IDs are
  normalized by the shared mutation.
- Shelf creation was traced against both active Android Library layouts. Android
  currently creates manual shelves from a single trimmed, nonblank name and can
  seed them from the contextual book selection; iOS already follows that
  contract. The dormant shared smart-collection engine was intentionally not
  exposed as an iOS-only creation feature.
- Permanent book and shelf confirmations on iOS now use Android's operation
  labels: destructive library actions say Delete, while recents and
  shelf-membership actions retain Remove. Folder-backed deletion also colors
  the Delete action as an error and states that the source files are removed
  irreversibly.
- iOS active PDF tabs now enforce Android's twenty-tab ceiling before opening a
  new reader session, while still allowing an existing tab to be reactivated.
  Close all now requires Android's error-styled confirmation instead of
  discarding the whole session set immediately.
- Active PDF tabs are now available inside the iOS reader when chrome is
  visible, matching Android's default top strip. Tabs can be switched or closed
  in place, closing the active tab moves to the most recent remaining tab,
  closing the final tab exits the reader, and the plus action lists unopened
  library PDFs by recency. The PDF drawer also exposes Android's Tabs section
  and a persisted Show top tab strip switch. The shared close transition keeps
  projected tab objects and selected-reader identity aligned instead of leaving
  stale Home chips.
- Android's no-limit recent-books value now survives iOS persistence as `0`.
  The previous mobile snapshot writer silently converted it to legacy `12`,
  which reloaded as a ten-book limit. Invalid legacy values are normalized to
  the nearest Android option before writing, with round-trip coverage for the
  unlimited case.
- The global Folder Sync toggle now survives iOS relaunches independently from
  the linked-folder records, matching Android's persisted off-by-default
  preference. Snapshot schema 27 stores the flag; older snapshots safely
  restore it as disabled.
- Shelf projection is no longer lossy when iOS persists the library. Smart
  shelves retain their Android-compatible rule JSON, manual shelves retain the
  original per-membership `addedAt` timestamps, and smart shelves no longer
  emit meaningless static book references. Generated tag, series, and folder
  shelves remain excluded from durable records.
- Library view state now survives iOS relaunches at Android's durability
  boundary. Sort order, applied file/folder/read-status/tag filters, the Home
  versus Library landing page, and the selected Library sub-tab are stored in
  snapshot schema 28. Older snapshots restore Android's defaults, unknown enum
  values are ignored safely, and persisted page indices are range-clamped.
- Interrupted reader sessions now restore on iOS like Android. The device-local
  marker records the open book ID and file type, restores only when the matching
  library record and physical file remain available, follows active PDF tab
  switches, and is cleared on an intentional reader exit. It is deliberately
  kept outside the cloud snapshot so one device cannot open another device's
  transient reader screen.
- External-file retention now follows Android's managed-copy lifecycle. `ASK`
  opens the book first and presents the non-dismissible Keep/Remove decision
  only after the reader closes, `DELETE` removes the managed copy on close,
  `KEEP` retains it, and `TEMPORARY` never enters the library. The “don't ask
  again” choice persists the matching default. Pending managed copies are
  recorded device-locally and cleaned after an interrupted session, while
  reopening bytes that already belong to a library book does not put that
  existing record at risk.
- Shelf navigation now restores at Android's device-local boundary. iOS
  remembers the open shelf, whether its Add Books workflow was active, and the
  selected Unshelved/All Books source; stale shelf IDs normalize back to the
  shelf overview. Entering, backing out of, completing, or deleting the viewed
  shelf clears the nested workflow consistently, including its transient book
  selection.
- The iOS Settings cloud-sync switch now reflects and persists the real
  device-local toggle instead of always rendering off. Startup preserves the
  saved preference while Firebase/Google account state is still loading, then
  disables and persists it only after a resolved account proves Drive sync is
  ineligible. Google linkage plus Drive authorization remains mandatory for
  sync; Apple-linked Pro accounts continue to work for non-sync Pro features.
- Folder Sync toggles now have Android's exact side effects from both Settings
  and the drawer: changing the switch never opens a folder picker, and enabling
  it requests cloud work only when cloud sync is already active and eligible.
  Linking a folder remains an explicit action in the Library Folders tab.
- Native iOS folder refresh now delivers an ordered batch instead of a single
  overwrite-prone pending slot. Every linked folder is reconciled during Scan
  All, repeat results replace only that folder's queued work, failed scans keep
  the previous library contents, and the shared refreshing state remains active
  until the complete native batch is consumed. Successful scans update both
  per-folder and global last-scan timestamps through the shared sync engine.
- iOS bulk imports now retain the complete picker outcome instead of silently
  dropping native copy/hash failures through `compactMap`. The shared Android
  import planner classifies each copied file as added, duplicate, or unsupported,
  combines those counts with native failures, removes rejected managed copies,
  and applies Android's feedback priority. Cancelling the picker remains silent
  and is no longer misreported as an import failure.
- Password-protected PDFs now follow Android's open contract on iOS. PDFium's
  password error is distinguished from corrupt-document failures, a
  non-dismissible password prompt retries the document without persisting the
  secret, and an unsuccessful retry shows an incorrect-password state.
  The transient password is forwarded to page and zoom-tile rendering, search,
  outline loading, text selection, links, and read-aloud extraction so the
  complete reader remains functional after unlock.
- PDF toolbar navigation now respects Android's TTS lifecycle on iOS. Slider,
  Sidebar, and Search are disabled while speech is speaking or preparing in
  top, bottom, and hidden-tool placements, then re-enable when speech pauses or
  stops. Hidden tools and Share/Save/Print children also dismiss the overflow
  before opening their destination, preventing a stale menu from covering the
  resulting sheet or native controller.
- User-driven PDF pagination now stops active or preparing speech like Android.
  Both direct pager drags and tap-to-turn edge navigation cancel the pending
  speech request, active utterance, and highlight before changing pages.
  Programmatic TTS page advancement remains uninterrupted, vertical scrolling
  retains its existing behavior, and paused speech is not discarded.
- PDF Auto Scroll now moves the vertical document continuously from the frame
  clock instead of jumping one full page every 3.5 seconds. Its shared speed
  contract matches Android's effective 80 px/s base with the 0.5 multiplier
  (the default 3x setting is 120 px/s), and iOS now has play/pause, speed, top,
  close, collapse, stepper/slider, and min/max controls. Global profiles persist
  across files, local profiles are stored with the PDF and snapshot, and bounds
  apply Android's correction rules. Musician mode persists globally, suppresses
  chrome, and exposes the same quarter-width page-jump regions; user drags and
  musician jumps use Android's temporary-pause timings. Leaving vertical mode
  closes the session.
- PDF search now follows Android's live-query lifecycle on iOS: it accepts
  single-character terms, waits the same 300 ms before searching, and requests
  keyboard focus after Android's 100 ms activation delay. PDFium text extraction
  runs off the main dispatcher, checks cancellation between pages when the query
  changes, and caches the completed per-document index so subsequent edits search
  existing text instead of reopening and rescanning every page. Search results
  now convert their UTF-16 ranges to PDFium page rectangles, share the page text
  session with selection, and render in both pagination and vertical modes.
  The navigation pill exposes Android's all/focused highlight toggle and disables
  previous/next at the result boundaries instead of wrapping to the other end.
  Background matches use Android's translucent yellow with 3 px padding; the
  active match is always layered above them in orange with 5 px padding and a
  3 dp orange border, including when focused-only mode hides background matches.
- PDF page-slider movement no longer pollutes semantic jump history on iOS.
  Android's history remains limited to TOC/bookmark/highlight, internal-link,
  and search-result jumps, with the same branch truncation, adjacent reverse
  replacement, invalid-page pruning, and 21-entry cap. The page-slider enabled
  preference is now retained independently for each PDF, as on Android.
- External EPUB links now use Android's confirmation flow in both paginated and
  vertical modes. iOS shows the complete destination and offers Open, Copy, or
  Cancel instead of launching an external app immediately. EPUB auto-scroll
  also applies Android's effective 0.5 speed multiplier, so the same stored
  setting moves the viewport at the same rate on both platforms.
- The EPUB page-slider toggle is now persisted independently per book on iOS,
  matching Android across reader reopen. Search temporarily suppresses the
  slider chrome without clearing that preference, and leaving a reading mode
  still disables and persists the slider when Android does.
- EPUB Auto Scroll now uses Android's actual `0.1x–10x` profile rather than the
  earlier iOS-only pixel-speed scale. The default is `0.8x`; the renderer
  converts Android's half-pixel-per-frame multiplier to a time-based 60 Hz
  rate. Global and per-book speed/min/max profiles apply the same bound
  correction rules, persist across reopen, and expose Android's exact speed
  choices plus the persisted slider/stepper input toggle. Existing legacy iOS
  pixel-speed values migrate to equivalent Android multipliers.
- EPUB musician mode now persists globally and suppresses reader chrome while
  active. Its left/right regions match Android's quarter-width, 40%-height
  layout: taps move by 75% of the viewport with a 600 ms auto-scroll pause,
  while a one-second hold shows progress and jumps to the chapter start/end
  with a one-second pause. Ordinary vertical drags temporarily pause scrolling
  for Android's 300 ms interval, and closing Auto Scroll restores chrome.
- EPUB search activation now requests keyboard focus after Android's 100 ms
  delay. Typing retains the 350 ms debounce, while the keyboard Search action
  runs immediately, hides the keyboard, and clears focus. Choosing a result
  collapses only the result panel and leaves Android's search top bar plus
  boundary-aware navigation visible; closing search clears the query, results,
  active index, keyboard focus, and stale navigation state.
- Automatic EPUB chapter-end events are now distinct from manual pull
  navigation. Auto Scroll advances to the next chapter, pauses for Android's
  one-second load interval, then resumes at the chapter start. Reaching the
  final chapter stops the playing state while keeping the Auto Scroll controls
  open, instead of leaving iOS showing a running session whose JavaScript timer
  had already terminated.
- EPUB Auto Scroll controls now include Android's transient collapsed shell:
  collapse reduces the panel to expand and play/pause actions without changing
  the saved profile or active session. The expanded panel is capped at 400 dp
  and includes Scroll to top, which uses the same one-second temporary pause as
  Android before resuming playback.

## Known platform constraint

Android's screen-capture preference applies `FLAG_SECURE` to the activity. iOS
does not provide a supported equivalent that blocks screenshots. The
frequently suggested secure-text-field canvas technique depends on undocumented
UIKit internals and is intentionally not shipped; a supported recording/privacy
overlay can be added later, but must not be presented as exact screenshot
protection.

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
