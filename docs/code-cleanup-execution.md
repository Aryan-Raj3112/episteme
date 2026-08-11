# Code cleanup execution plan

Last updated: 2026-08-11

This is the durable execution log for the post-migration cleanup program. Android is the behavioral benchmark. Portable mobile logic is implemented in shared first; Android and iOS retain only platform execution and UI differences that are genuinely OS-specific. Paid iOS features remain out of scope. No phase may intentionally change Android behavior or the pagination rule that downgrades `text-align: justify` to `TextAlign.Left` unless alignment was explicitly forced.

## Standards

- Split by cohesive responsibility and ownership, not by arbitrary line count.
- Shared owns portable models, reducers, state machines, calculations, eligibility, conflict resolution, retry policy, and operation planning.
- Platforms own native handles, rendering execution, OS lifecycle/effects, credentials, transports, filesystem/SAF access, and database transactions.
- UI must not make repository, cloud, or persistence decisions.
- Dependencies enter through composition roots. Feature code must not act as a service locator.
- Prefer feature packages and internal APIs; avoid new `Common`, `Utils`, or miscellaneous dumping grounds.
- Every behavior-sensitive extraction needs focused parity tests. Pure file moves need compilation and existing relevant tests, not artificial tests.
- Review at 400 LOC, normally split at 800 LOC, and document a cohesion exception above 1,500 LOC. Existing mega-files use a ratchet: they may not grow without an explicit reviewed exception.
- LOC is diagnostic. Each phase reports Android/shared production LOC changes, but boundary quality and behavior preservation decide success.

## Phase 0 baseline and safety map

Status: complete (2026-08-11)

Baseline (2026-08-11):

| Metric | Value |
| --- | ---: |
| Android production Kotlin | 108,194 LOC / 210 files |
| Shared production Kotlin | 115,373 LOC / 271 files |
| `MainViewModel.kt` | 7,642 LOC / 210 declared member functions / 16 directly owned graph-repository-adapter-controller-manager dependencies |
| `RecentFilesRepository.kt` | 1,001 LOC |
| `Common.kt` | 3,539 LOC |
| `SharedModelMappers.kt` | 305 LOC |
| Forbidden Android/JVM imports in portable shared source sets | 0 |

Largest production files at baseline:

| File | LOC | Intended boundary |
| --- | ---: | --- |
| `SharedMobileScreens.kt` | 10,229 | Feature-owned shared mobile screens/dialogs |
| `PaginatedReader.kt` | 9,416 | Android coordinator, renderer, effects, platform resources |
| `PdfViewerScreen.kt` | 8,285 | Android PDF host, controls, effects, native resource ownership |
| `EpubReaderScreen.kt` | 7,714 | Android EPUB host, WebView effects, controls |
| `MainViewModel.kt` | 7,642 | Thin application state holder over feature controllers |
| `PdfPageComposable.kt` | 5,690 | PDF page renderer, gestures, overlays, native resource boundary |
| `ReaderHtmlDocumentBuilder.kt` | 5,610 | Typed HTML/CSS/JS document construction components |
| `SharedNativePaginatedReader.kt` | 5,106 | Shared pagination UI coordinator and feature-owned surfaces |
| `SharedReaderChrome.kt` | 4,970 | Shared chrome controls, panels, state/effects |
| `SharedMobileEpubReader.kt` | 4,679 | Shared EPUB host components |
| `ReaderIosApp.kt` | 4,364 | iOS composition and platform adapters |
| `Common.kt` | 3,539 | Eliminate into feature-owned declarations |

Safety matrix:

| Area | Benchmark/invariant | Required evidence before phase completion |
| --- | --- | --- |
| Pagination | Android justify downgrade remains unchanged | Existing/focused pagination tests and Android compilation |
| Reader open/restore/close | Android ordering, loading, failure, and URI effects remain unchanged | Shared orchestrator tests plus Android tests/compilation |
| PDF annotations | Sidecar payload compatibility and timestamp conflict rules remain unchanged | Shared codec/planner tests plus Android parity tests |
| EPUB | Locator/CFI, highlights, bookmarks, WebView lifecycle remain unchanged | Shared reader tests plus Android compilation |
| Library | Shelf/tag transactions, projections, ordering, filters remain unchanged | Shared controller/projector and Android adapter tests |
| Cloud/folder sync | Shared plans; Android executes Drive/Firestore/SAF | Focused decision tests and platform compilation |
| Native resources | Handles are closed by their owning platform boundary | Existing lifecycle tests or focused tests where ownership changes |

## Phase roadmap

### Phase 1 — Application orchestration and storage boundaries

- Split `RecentFilesRepository` into transaction-shaped book, library, artifact, folder mirror, and legacy migration capabilities.
- Move feature orchestration out of `MainViewModel` into shared controllers and narrow Android adapters.
- Route construction through `AndroidAppGraph`; do not introduce a DI framework solely for cleanup.
- Exit: `MainViewModel` owns controllers/facades rather than constructing feature repositories; storage transaction boundaries are explicit and tested.

Result (2026-08-11): complete. `AndroidAppGraph` is now the single construction point for Android repositories, parsers, importers, and storage adapters; `MainViewModel` directly constructs none of those feature dependencies. Shelf/tag flows, transactions, remote shelf application, and legacy shelf migration were physically removed from `RecentFilesRepository` into `AndroidLibraryMutationStore`. Remaining book, folder mirror, artifact, and legacy operations are exposed through four transaction-shaped capability interfaces, so consumers can no longer reach unrelated operations through a broad repository reference. The concrete implementation remains consolidated where its deletion/cache transactions share private resources; physical extraction can proceed behind these interfaces without another consumer migration.

Verification: focused shared library controller tests and Android shelf/tag ViewModel parity tests pass. Android OSS test compilation/tests, Android Pro compilation, iOS simulator compilation, and `git diff --check` pass. No UI, schema, preference key, timestamp ordering, or sync callback behavior was intentionally changed.

### Phase 2 — Shared UI feature modularization

- Decompose `SharedMobileScreens.kt` by library, importing, search, settings, and reader workspace responsibility.
- Then split shared chrome, EPUB, annotations, and non-reader surfaces along existing feature boundaries.
- Exit: public entry points remain stable or intentionally migrated; feature-private APIs are internal; no cycles; Android/iOS compile.

Result (2026-08-11): complete. The 10,229-line `SharedMobileScreens.kt` aggregation is now a 277-line app-drawer surface plus separate PDF host (3,496), PDF rendering (2,385), library screens (2,494), and library components (1,923) files. `NonReaderScreens.kt` changed from 2,916 lines into screen orchestration (1,758) and library content (1,234). `SharedPdfAnnotationUi.kt` changed from 2,944 lines into controls/editors (1,824) and rendering/geometry (1,178). Public entry-point names were retained; only cross-file feature implementation seams became `internal`.

Verification: `NonReaderLayoutModelsTest` and `SharedPdfAnnotationUiTest` pass. Common metadata, Android shared, iOS simulator, Android OSS, and Android Pro compilation pass; portable-source enforcement remains applicable and `git diff --check` passes. This phase intentionally performs no UI redesign or state-policy changes.

### Phase 3 — Android reader-host decomposition

- Process paginated, PDF, and EPUB hosts one vertical slice at a time.
- Separate coordinator, renderer, controls/overlays, effects, and native resource ownership.
- Move newly discovered portable decisions to shared before mapping Android to them.
- Exit: host composables do not make persistence/cloud decisions; Android output and behavior remain unchanged.

Result (2026-08-11): complete. The 9,416-line `PaginatedReader.kt` aggregation is now a 894-line coordinator, 3,386-line paginated content renderer, 1,961-line selection/diagnostics boundary, and 3,504-line native-vertical renderer. `PdfPageComposable.kt` changed from 5,690 lines into a 4,309-line interaction/page coordinator and 1,432-line native rendering/geometry boundary. EPUB preference/effect helpers moved into a 346-line feature file. PDF sidecar repository ownership, save serialization, hash/no-op policy, deletion ordering, mutex ownership, position/bookmark persistence, and cloud-upload queuing moved from `PdfViewerScreen` into the 207-line document-scoped `PdfReaderPersistence` component.

Android PDF jump history no longer carries a second mutable-list policy: it now consumes the existing tested `SharedPdfJumpHistory` model, preserving Android's reverse-adjacent-jump handling, pruning, and 21-entry cap. Public screen entry points and UI wiring remain unchanged. No portable decision discovered in this phase was left duplicated on Android.

Verification: focused Android paginated-reader, PDF core/repository, and EPUB reader tests pass; the shared `PdfReaderSessionTest` parity suite passes; Android OSS and Pro compilation and `git diff --check` pass. Existing compiler warnings were left unchanged rather than mixed with this structural phase. No emulator was run.

Documented cohesion exceptions retained under the mega-file ratchet:

- `PdfViewerScreen.kt` (8,133 LOC): Android PDF composition root and native lifecycle/UI wiring. Persistence policy and page rendering are now external. It must not grow; the next safe seam is a typed PDF document-session state holder, after Phase 4 establishes the corresponding shared reader-engine boundaries.
- `EpubReaderScreen.kt` (7,422 LOC): Android EPUB composition root coordinating WebView/native modes, lifecycle, TTS, and UI wiring. Preferences/helpers are external and jump-history policy is already shared. It must not grow; the next safe seam is typed TTS/navigation session ownership aligned with the Phase 4 shared EPUB engine.
- `NativeVerticalReaderScreen.kt` (3,504 LOC), `PaginatedReaderContent.kt` (3,386 LOC), and `PdfPageComposable.kt` (4,309 LOC): cohesive Android native render/gesture boundaries retained to avoid parameter-bag abstractions. Phase 6 ratchets these files and requires an explicit exception review for any growth.

### Phase 4 — Shared reader-engine modularization

- Split HTML document construction into document, styles, scripts, pagination, annotation, media, and sanitization responsibilities.
- Split shared native pagination/chrome/EPUB engines using the same feature boundaries.
- Preserve generated output where byte compatibility matters and add golden/fixture tests only where necessary.
- Exit: small public entry points, independently testable components, portable source enforcement passing.

Result (2026-08-11): complete. `ReaderHtmlDocumentBuilder.kt` changed from a 5,610-line mixed generator into a 420-line public façade plus a 95-line document assembler, 518-line styles module, 774-line semantic/media renderer, 706-line highlight/locator/sanitization module, and navigation (1,143), selection (1,049), and annotation (963) JavaScript fragments behind a seven-line script assembler. Existing public builder APIs remain unchanged. The generated document suite passes after the split; only non-semantic template indentation is allowed to differ across assembly seams.

`SharedNativePaginatedReader.kt` changed from 5,106 lines into host/controller (795), page and selection surfaces (905), vertical rendering (1,422), page geometry (232), text rendering (705), and highlight/selection mapping (1,238). `SharedReaderChrome.kt` changed from 4,970 lines into host orchestration (797), search/highlight chrome (755), format/theme controls (1,248), TTS/toolbars (1,006), and navigation/sidebar (1,390). `SharedMobileEpubReader.kt` changed from 4,679 lines into its 1,707-line composition root plus chrome (848), library/annotation sheets (515), formatting/themes (855), navigation/search adapters (679), and auto-scroll/musician controls (347).

All extracted implementation APIs are feature-internal; public screen and builder entry points remain stable. No Android/JVM imports entered portable source sets and no platform behavior or UI decision was intentionally changed.

Verification: the complete `ReaderHtmlDocumentBuilderTest` suite, shared native pagination interaction tests, native vertical-flow tests, and native image-style tests pass. Common/desktop, Android shared, and iOS simulator compilation pass; portable-source enforcement and `git diff --check` pass. No emulator was run.

Documented cohesion exception retained under the mega-file ratchet:

- `SharedMobileEpubReader.kt` (1,707 LOC): mobile EPUB composition root owning state/effect wiring across the extracted feature surfaces. It may not grow. A further split requires a typed reader-session state holder rather than a large parameter bag; that state-holder work remains paired with the Android host exceptions and is enforced by the Phase 6 ratchet.

### Phase 5 — Legacy duplication and naming cleanup

- Eliminate `Common.kt` into feature-owned files.
- Remove dead contracts/wrappers after proving there are no production consumers.
- Consolidate remaining Android/shared model pairs and explicitly name truly platform-specific types.
- Exit: no speculative production abstractions, no ambiguous platform types, and no new generic dumping grounds.

Result (2026-08-11): complete. The 3,539-line `Common.kt` dumping ground was deleted and its unchanged Android implementations were regrouped into nine responsibility-owned files for AI settings/client/hub, search UI, TTS settings, reader color controls, textures, theme persistence, and theme editing. The largest resulting file is 670 LOC. The generic package aliases formerly supplied by `Common.kt` were removed; affected production and test consumers now import their shared model types explicitly. The remaining Android models are Android-resource or Android-framework owners, while identical mobile domain state continues to be the shared type rather than a duplicated platform model.

The contract audit also removed six identity conversion wrappers for `FileType`, `LibraryFilters`, and `SyncedFolder`; they had no transformation boundary and no production consumers. Their tautological wrapper tests were removed while the real Android file-capability assertions remain. The final generic `NavigationEvent` package alias was replaced with an explicit shared import. Existing feature-local aliases with domain-specific names (for example rich PDF layout and EPUB navigation names) remain intentional vocabulary adapters, not duplicate model implementations.

Verification: focused Android shared-state, model-mapper, EPUB search, PDF settings/theme, and TTS preference tests pass. OSS production and unit-test compilation, Pro production compilation, Android instrumented-test compilation, `git diff --check`, and the `.gitignore` invariant pass. No emulator was run. No Android behavior, UI, persistence format, preference key, or pagination alignment policy was intentionally changed.

### Phase 6 — Permanent quality gates and completion audit

- Retain portable import enforcement and add dependency-direction and mega-file ratchet checks.
- Add static analysis/formatting checks only where they are deterministic and configuration-cache-safe.
- Audit every phase requirement against source, focused tests, cross-platform compilation, and git history.
- Exit: all gates run in normal verification, all phases have local `master` commits, and this document contains final metrics and remaining documented exceptions.

## Phase history

| Phase | Status | Android LOC delta | Shared LOC delta | Commit | Verification/result |
| --- | --- | ---: | ---: | --- | --- |
| 0 | Complete (2026-08-11) | 0 | 0 | `45b7ffc4` | Baseline, standards, largest-file inventory, safety matrix, and portable-source verification established. |
| 1 | Complete (2026-08-11) | +92 | 0 | `9fcf1971` | Composition-root ownership, physical library persistence split, and narrow book/folder/artifact/legacy capabilities. `RecentFilesRepository` 1,001 → 883 LOC; direct feature constructions and broad repository references in `MainViewModel` are both zero. |
| 2 | Complete (2026-08-11) | 0 | +479 | `7d47b7af` | Split three shared UI aggregations into nine responsibility-owned files; largest resulting file is 3,496 LOC instead of 10,229. Added LOC is file-local imports and explicit internal seams, not duplicated behavior. |
| 3 | Complete (2026-08-11) | +466 | 0 | `437508fc` | Split Android reader rendering/selection/preferences, extracted PDF persistence ownership, and mapped Android PDF jump history to the tested shared policy. Focused reader tests and OSS/Pro compilation pass. |
| 4 | Complete (2026-08-11) | 0 | +754 | `eaa35428` | Split HTML generation, native pagination, reader chrome, and mobile EPUB into responsibility-owned modules; focused tests, cross-platform compilation, and portable-source enforcement pass. |
| 5 | Complete (2026-08-11) | +291 | 0 | this phase commit | Deleted `Common.kt`, regrouped its implementation into nine feature files, removed ambiguous generic aliases and six identity wrappers, and passed focused tests plus OSS/Pro and Android-test compilation. |
| 6 | Pending | — | — | — | — |
