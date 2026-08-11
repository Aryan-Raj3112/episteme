# iOS ↔ Android parity: authoritative audit and implementation plan

Date: 2026-08-11

This document is the authoritative forward-looking parity plan. Historical work logs remain in
`ios-android-parity.md`, `ios-parity.md`, and `android-shared-mobile-migration.md`; their completed
entries are evidence of earlier migrations, but their summary tables are not current enough to be
used as the backlog.

Android remains the behavioral and visual benchmark. Portable behavior is reconciled to Android in
shared code first, then both platforms consume it. Platform code retains only native rendering,
files, permissions, lifecycle, audio/session integration, sharing, authentication, billing, and
system UI. Paid iOS AI/cloud-TTS work remains deferred.

## Audit method and evidence standard

This audit inspected current production source, source-set wiring, capability gates, platform
actuals, tests, and the three historical parity documents. Status terms mean:

- **Shared/proven**: one portable implementation is used by both platforms and focused tests cover
  the relevant decision.
- **Implemented/static**: current source contains the iOS path and cross-platform compilation has
  passed, but native runtime parity has not been observed.
- **Partial**: a concrete behavior, integration, or ownership gap remains in current source.
- **Runtime-required**: static source cannot prove timing, gesture, lifecycle, rendering, or native
  controller behavior.
- **Deferred**: intentionally outside the current free/non-auth scope.
- **Intentional deviation**: exact Android behavior is unsupported or inappropriate on iOS.

Compilation is not accepted as runtime parity. A historical “DONE” entry is not accepted as proof
when it records only compilation or shared unit tests. No iOS simulator was run during this audit,
in accordance with the project rule not to run emulators unless explicitly requested.

## Executive conclusion

Android and iOS are aligned at the model/policy layer far more than at the runtime-verification
layer. Most new portable core work can and should be implemented in shared. They are not yet “on the
same page” in the stronger sense that a feature is implemented once, exercised by both native
adapters, and proven on both platforms.

The principal remaining risks are:

1. iOS runtime behavior remains under-tested. `iosTest` now has focused metadata, PDF export,
   settings-reachability, and bridge-effect tests, but these do not replace native runtime evidence.
2. `ReaderIosApp.kt` remains a 4,359-line application composition root with library, import, folder,
   reader, settings, persistence, and native-effect orchestration. Portable changes can still be
   implemented directly in this iOS host instead of through a shared controller.
3. Android reader hosts and the iOS shared-mobile reader hosts are not identical entry points.
   They share engines, policies, models, and many UI components, but host wiring and native effects
   can diverge.
4. The historical parity summaries mix code-complete, compile-complete, and runtime-complete states.
5. Cloud shelves/annotation sidecars/retry/device management and paid AI/cloud TTS remain incomplete
   by explicit project direction.

Accordingly, the next work should be parity-driven vertical slices, not another general cleanup or
bulk LOC migration.

## Current ownership audit

| Concern | Canonical owner now | Android boundary | iOS boundary | Audit result |
| --- | --- | --- | --- | --- |
| File capability/type policy | `SharedFileCapabilities` | Android picker, URI and loaders | iOS picker, managed paths and loaders | Shared/proven statically; native corrupt/edge cases need runtime audit |
| Library reducers, filters, selection, pins, tabs | shared state/reducers/projectors | Room projection and Android effects | snapshot state and native file effects | Shared policy; platform persistence/effect sequences still require parity tests |
| Shelves/tags/folder projection | shared editor/controllers/policies | Room transactions and SAF | snapshot mutation and security-scoped folders | Implemented/static; runtime folder failure/recovery audit required |
| Import identity/planning | shared capabilities/import planner/hash policy | content URI/cache/import adapters | native picker/copy/hash adapters | Implemented/static; duplicate/corrupt/batch feedback runtime audit required |
| Reader open/close state | shared app-reader session actions | Android ViewModel and URI effects | iOS app host and managed paths | Shared transitions; interruption/restoration runtime audit required |
| EPUB parsing/render plans/navigation | shared package loaders, reader engines and UI policy | Android WebView/native renderer hosts | shared mobile screen plus WKWebView/native actuals | Mostly shared; platform host and WebView lifecycle parity unproven |
| EPUB local TTS contract | shared TTS state/follow/replacement policy | Android TTS/service adapter | AVSpeechSynthesizer/audio-session adapter | Same contract, different native lifecycle; P0 runtime audit |
| PDF reader state/navigation/annotations | shared PDF session, reducers, codecs and UI | Android PDFium/render/effect adapters | iOS PDFium/render/effect adapters | Broad shared ownership; gesture/render/native action parity unproven |
| PDF search/selection/links | shared policy and UI plus platform text sessions | Android PDFium text adapter | iOS PDFium text adapter | Implemented/static; geometry/cancellation/link runtime audit required |
| PDF rich text/virtual pages/reflow | shared models, controllers and policies | Android font/repository/render adapters | iOS shared screen and native reflow adapter | Implemented/static; end-to-end edit/persist/export parity unproven |
| Reader appearance/toolbars | shared settings/models/components | Android resource/persistence adapters | iOS defaults/persistence adapters | Shared policy; visual and migration runtime audit required |
| Local audiobook/TTS listening | shared playback/listen state and handoff policy | Media3/service/notification | AVPlayer/AVSpeech/control-center | Implemented/static; interruption/background/remote-control parity required |
| Settings hierarchy | shared settings model/UI | Android resources/effects | iOS capability-gated effects | Shared structure; several platform actions need explicit reachability audit |
| App appearance/fonts | shared models/UI | Android font/resource loading | iOS font import/CoreText/Google Fonts | Implemented/static; visual and persistence audit required |
| Account/entitlements | shared eligibility rules | Firebase/Play Billing | Firebase/StoreKit | Partial: trusted provider-link/account merge remains external/auth work |
| Cloud book metadata/content/tombstones | shared merge policy | Firestore/Drive execution | Swift/Kotlin bridge execution | Implemented/static for books; broader sync deferred |
| Cloud shelves/PDF-EPUB sidecars/retry/devices | shared planners only in parts | Android production integrations | incomplete iOS integration | Deferred until iOS auth readiness |

## Concrete current gaps

### P0 — Native runtime verification and testability

- The iOS target now has four focused native test files (318 LOC). Shared/desktop and iOS adapter
  tests prove portable policy and selected bridge sequencing, but do not directly exercise all of
  AVSpeechSynthesizer, AVAudioSession, WKWebView, PDFium rendering sessions, UIKit controllers, or
  security-scoped folder-provider behavior.
- No current evidence proves the complete free/non-auth flow on both phone and tablet-class iOS
  viewports after the latest migrations.
- Timing-sensitive behavior (debounce, interruption, auto-scroll, chapter/page transitions, gesture
  cancellation, background snapshots) must be observed or tested through injectable native ports.

### P0 — iOS application orchestration remains too broad

- `ReaderIosApp.kt` owns application state, persistence, imports, folders, reader selection,
  metadata editing, sync requests, settings actions, native action routing, and large UI composition.
- Shared reducers are used extensively, but sequencing and failure behavior can still be authored in
  the iOS file instead of one shared feature controller.
- The next portable feature should not add another callback or state branch to this host. Extract a
  shared controller/use case first and leave the iOS file as a native-effect adapter/composition root.

### P0 — EPUB/TTS runtime parity

Static source shows shared search, navigation, bookmark, annotation, auto-scroll, musician-mode,
reader settings, and TTS contracts. Remaining proof/work:

- AVSpeech interruption, route change, lock-screen/control-center, pause/resume offset, completion,
  failure, and release behavior versus Android's foreground-service benchmark.
- Scene background snapshot and foreground TTS relocation ordering.
- WKWebView load/reload, appearance update, navigation request, internal/external link, selection,
  and chapter-transition ordering.
- WebView vertical, native vertical, paginated LTR, and paginated RTL restoration to the same locator.
- Search cancellation and exact source-offset navigation during rapid query/chapter changes.
- Auto-scroll final-chapter stop, collapsed controls, musician long-press/tap regions, and manual
  drag pause timing.
- Image open/share and external lookup failure paths.

### P0 — PDF runtime parity

Static source shows broad shared ownership, but the following remain runtime-required:

- Pagination/vertical mode switching, first-page-alone spreads, RTL, blank virtual pages, and saved
  position restoration.
- Pan/zoom/tile transitions, one-hand zoom, lock-panning, orientation changes, short documents, and
  viewport resize/reset behavior.
- Search cancellation/index reuse, UTF-16 range geometry, focused/all highlights, and result
  navigation history.
- Selection handles, magnifier, lookup actions, saved-highlight read-aloud offsets, and link hit
  priority.
- Ink/text-box/rich-text edits, keyboard/focus behavior, virtual-page remap, immediate/debounced
  persistence, reopen, export, and corruption recovery.
- Embedded annotation threads and annotation-sidecar freshness/conflict behavior.
- Reflow generation/cancellation/cache/open behavior and password forwarding.
- Local TTS cancellation on user pagination and uninterrupted programmatic advancement.
- Native Share, Save Copy, and Print presentation/dismissal on phone and iPad.

`SharedMobilePdfNativeAction.INSERT_BLANK_PAGE` is a stale enum member rather than an iOS parity
gap: the active shared screen performs blank-page insertion through shared reader state directly.
Remove the dead enum member during the PDF slice after confirming no binary/API consumer.

### P1 — Library, import, and folder behavior

Current source implements shared planning and substantial iOS native handling. Remaining proof/work:

- Picker cancellation versus partial copy/hash failure feedback.
- Exact duplicate identity across picker, folder, OPDS and external-open paths.
- Unsupported, malformed, encrypted/DRM, corrupt, and password-protected inputs for every advertised
  iOS type.
- Metadata extraction/backfill parity for EPUB, PDF, MOBI, comics, DOCX/ODT/FODT/PPTX and text.
- Folder-provider cancellation, permission loss, partial scan failure, changed bytes, rename/move,
  source deletion, write-back, and multi-folder ordered batches.
- Managed-file cleanup after delete, interrupted external sessions, failed imports, and metadata
  replacement.
- Snapshot migration for tabs, pins, shelves, filters, folder identities and reading state.

### P1 — Settings and platform integration

- The apparent iOS `Unit` cases for text/PDF defaults, toolbar and TTS replacements are not current
  feature gaps: `SharedSettingsHub` converts those rows to typed shared destinations before the
  platform action callback, and the local-override note is a non-interactive shared surface. Retain
  a reachability test so a future settings-model change cannot accidentally expose an inert action.
- Verify app/reader appearance, custom fonts, language relaunch, recent limit, external-file mode,
  strict filtering, PDF filename preference, tabs, folder sync, sign-out and reflow-cache clearing
  across relaunch.
- Verify deep/open-URL routing distinguishes authentication callbacks from imported documents and
  restores the correct reader/session state.
- Verify background audio, remote controls, interruptions and system UI/orientation/brightness reset.
- Global cloud/device TTS settings are not equivalent to Android and remain capability-gated.

### P2 — Visual/accessibility parity

- Compare Home, Library, shelf/folder detail, Settings, EPUB and PDF at phone and tablet widths.
- Verify spacing, menus, action availability, destructive copy, empty/error/loading states, sheets,
  keyboard avoidance, safe areas, rotation and dark mode.
- Verify VoiceOver labels/order, selected state, focus restoration, Dynamic Type, touch targets and
  reduced motion.
- Android string resources and iOS shared literals can still differ cosmetically; normalize portable
  copy through shared localization rather than duplicating platform literals where feasible.

## Deferred and intentional differences

| Area | Classification | Rule |
| --- | --- | --- |
| Reader AI, BYOK UI, credits and cloud TTS on iOS | Deferred paid work | Do not expose placeholders or include in free parity exit criteria |
| Cloud shelves, annotation sidecars, retry diagnostics and device management | Deferred auth/cloud slice | Resume only after iOS authentication/provider-link contract is ready |
| Screenshot blocking | Intentional platform constraint | Do not imitate `FLAG_SECURE` with undocumented secure-text-field internals |
| Android Room/JVM book cache maintenance | Intentional platform difference | Keep hidden on iOS; shared policy must not require the cache |
| Android service/notification implementation | Platform-specific | Share lifecycle contracts; retain native Android/iOS execution |
| PDF thumbnail width difference | Accepted quality difference | Keep unless runtime memory/performance evidence requires alignment |
| Android bug-compatible blank thumbnail rendering | Rejected benchmark bug | Do not copy known Android defects into iOS/shared |

## Implementation roadmap

Each phase is one or more small vertical slices. Every behavior change starts with an Android
characterization test or direct source evidence, reconciles shared behavior to Android, maps both
platforms, and then verifies platform adapters. Record Android/shared/iOS production LOC deltas when
the phase completes.

### Phase 0 — Parity harness and backlog closure

1. Add a machine-readable parity inventory or focused source test for capability/action reachability.
2. Add injectable iOS ports around scene lifecycle, audio session/TTS, native document/share/print
   presentation, security-scoped folders, and clock/scheduling where needed for deterministic tests.
3. Add iOS tests for bridge/state sequencing without requiring UI screenshots.
4. Convert historical docs to links to this plan and mark their backlog tables historical.

Exit: every free/non-auth capability has an owner, handler, automated evidence, and runtime-test case.

Result (2026-08-11): complete. Every shared settings action now has an exhaustive iOS parity-scope
classification, and iOS settings dispatch can no longer hide a new action behind a catch-all branch.
The inventory records three free parity items for Phase 4 (`SCREEN_CAPTURE_PROTECTION`,
`HIDE_READER_AI`, and `CLEAR_BOOK_CACHE`); screenshot blocking and the Android-only JVM book cache
remain intentional platform constraints, while the portable hide-AI preference is an implementation
gap. Reader brightness/idle-timer effects and PDF native presentation now cross injectable iOS ports,
with native tests covering brightness restoration, keep-awake state, lifecycle event ordering, and
PDF action forwarding. Existing folder and audiobook callbacks are already injected bridge ports and
their behavioral slices remain in Phases 2 and 4. iOS test compilation passes without launching an
emulator. Production Kotlin changed Android 109,033 → 109,033 (0), shared 116,600 → 116,711
(+111), and iOS 9,964 → 10,075 (+111); iOS test source added 130 LOC.

### Phase 1 — Shared application feature controllers

Extract feature orchestration from `ReaderIosApp.kt` only through real vertical use cases:

1. import/external-open controller;
2. folder reconciliation controller;
3. reader-session restore/close controller;
4. metadata mutation controller;
5. settings persistence/effect commands.

Reuse existing shared reducers and narrow ports. Do not create a shared god ViewModel. Android must
consume the same controller before the slice is considered complete; Android behavior and storage
remain unchanged.

Exit: the relevant sequence is owned once in shared and both platform hosts execute typed effects.

Result (2026-08-11): complete. Android and iOS now consume shared decisions for external-file
open/close routing, terminal import outcomes, persisted reader-session validation, and portable
settings mutations. Native URI/file copying, security-scoped access, Room/defaults persistence,
localized feedback, and reader launching remain platform effects. Existing shared ownership was
retained where it was already the correct seam: `LocalFolderSyncEngine` reconciles folder scans for
both hosts, and the shared EPUB metadata editor/mutation policies own portable file and model edits.
No wrapper controller was added merely to rename those engines. The composition-root ratchet caught
initial host growth; persistence and translation adapters were moved into responsibility-owned files,
leaving `MainViewModel.kt` at 7,626 lines (below its 7,647 limit) and `ReaderIosApp.kt` at its 4,364-line
limit. Focused shared/Android tests, OSS and Pro compilation, iOS main/test compilation, the generic
arm64 iOS Swift build, `verifyCodebaseArchitecture`, and `git diff --check` pass without launching an
emulator. Production Kotlin changed Android 109,033 → 109,088 (+55), shared 116,711 → 116,897
(+186), and iOS 10,075 → 10,131 (+56); the Swift host changed by -7 LOC.

### Phase 2 — EPUB and local-TTS parity

Implement and verify the P0 EPUB/TTS list in lifecycle-sized slices: start/interrupt/resume/finish,
scene transitions, search/navigation, layout switching, auto-scroll/musician gestures, then native
actions. Add shared tests for portable decisions and iOS adapter tests for native event translation.

Exit: the Android benchmark matrix passes on iOS for all four reading modes and local TTS.

Result (2026-08-11): implementation and static/test pass complete; native runtime gate pending under
the project rule that an emulator is not launched without explicit user direction. Both iOS local
speech paths now translate AVAudioSession interruptions and output-route loss through one shared
policy, pause at the native word offset, resume only when the system permits, and release observers
and audio state deterministically. Existing shared lifecycle, search, four-layout restoration,
auto-scroll, musician-mode, image-action, and TTS progression policies were traced against Android
and their focused tests retained. Common tests, iOS test-source compilation, Android OSS compilation,
and the architecture gate pass without an emulator. Production Kotlin changed Android 109,088 →
109,088 (0), all shared production 116,897 → 117,646 (+749), and the iOS-specific Kotlin subset
10,131 → 10,286 (+155); Swift changed by 0 LOC. The runtime checklist remains required before final
parity closure and is not represented as proven by compilation.

### Phase 3 — PDF interaction parity

Proceed in this order to control risk:

1. open/password/restore and page/spread/RTL navigation;
2. zoom/pan/tile/orientation behavior;
3. search/selection/links/history;
4. TTS lifecycle;
5. ink/text boxes/embedded annotations;
6. rich text/virtual pages/persistence;
7. reflow/OCR and native file actions.

Remove stale compatibility APIs only after the relevant slice is routed and tested.

Exit: focused shared and platform tests pass, followed by a complete iOS PDF runtime checklist.

Result (2026-08-11): implementation and static/test pass complete; native runtime gate pending under
the project rule that an emulator is not launched without explicit user direction. Shared/iOS now
cover password-aware restore, spread/RTL navigation, zoom reset, search/selection/link handling,
manual-navigation TTS cancellation, close-time persistence, ink/highlight/comment/text/rich-text
Save Copy export, embedded PDF comment extraction and thread presentation, reflow password
forwarding, and native Share/Save Copy/Print routing. Export fails explicitly instead of silently
falling back to an unannotated source; changed virtual-page ordering remains unsupported because the
Android benchmark exporter also rejects it. Focused and full shared tests, Android OSS/Pro
compilation, iOS device/simulator main and simulator test-source compilation, architecture checks,
and `git diff --check` pass without an emulator. Production Kotlin changed Android 109,088 →
109,088 (0), all shared production 117,646 → 118,593 (+947), and the iOS-specific Kotlin subset
10,286 → 10,995 (+709); Swift changed by 0 LOC. Largest-file ratchets remain
`SharedMobilePdfRendering.kt` 2,385, `SharedMobilePdfReaderScreen.kt` 3,496, and
`ReaderIosApp.kt` 4,359 LOC. Runtime gesture, rendering, keyboard, persistence-reopen, and native
presentation checks remain required before final parity closure.

### Phase 4 — Library/import/folder/settings parity

Implement gaps found by the runtime matrix in transaction-shaped slices. Prefer shared intent-level
operations and typed platform effects over direct mutation in either platform host. Include snapshot
migration tests for every persisted model change.

Exit: import, duplicate, deletion, metadata, folder failure/recovery, settings and restoration cases
match Android or have a documented platform exception.

### Phase 5 — Visual and accessibility closure

After behavior is stable, perform screenshot/manual comparison on phone and tablet-class iOS
viewports, then fix shared UI where portable and use platform adaptation only for safe areas,
keyboard/system controllers, or native presentation.

Exit: visual/accessibility checklist complete with no unexplained action or state difference.

### Phase 6 — Cloud and paid work (separately authorized)

When authentication is ready, implement shelf sync, EPUB/PDF sidecars, retry/diagnostics and device
management through shared planners and native transport adapters. Paid AI/cloud-TTS work remains a
separate product phase and must not be mixed into free parity closure.

## Verification matrix for implementation phases

Every phase must select the relevant rows; the final closure requires all non-deferred rows.

| Gate | Required evidence |
| --- | --- |
| Android benchmark | Characterization tests and source trace before changing shared behavior |
| Shared policy | Focused common/desktop tests, including failure and stale-event cases |
| Android regression | OSS unit tests, Pro compilation, Android-test compilation; no behavior/UI change |
| iOS adapter | Native/iosTest coverage for event translation and failure paths |
| iOS build | iOS simulator framework compilation/link |
| Runtime | Explicit simulator checklist on phone and tablet-class viewport; no emulator without user request |
| Architecture | `verifyCodebaseArchitecture` and portable-source gate |
| Hygiene | `git diff --check`; `.gitignore` unchanged |
| Metrics | Android/shared/iOS production LOC before/after and largest-file ratchet status |

## Immediate implementation order

Phase 0 and the implementation/static portions of Phases 1–3 are complete. Continue with Phase 4 in
this order: import outcome/failure parity, folder reconciliation and permission-loss recovery,
snapshot migration/restoration, then settings/platform-effect reachability. Follow with Phase 5
visual/accessibility closure. Keep the Phase 2–3 native runtime matrices pending until simulator or
device execution is explicitly authorized. Do not begin cloud or paid work in parallel; only
transport-neutral foundations may be prepared before authentication is ready.
