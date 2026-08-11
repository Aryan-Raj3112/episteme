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

### Phase 2 — Shared UI feature modularization

- Decompose `SharedMobileScreens.kt` by library, importing, search, settings, and reader workspace responsibility.
- Then split shared chrome, EPUB, annotations, and non-reader surfaces along existing feature boundaries.
- Exit: public entry points remain stable or intentionally migrated; feature-private APIs are internal; no cycles; Android/iOS compile.

### Phase 3 — Android reader-host decomposition

- Process paginated, PDF, and EPUB hosts one vertical slice at a time.
- Separate coordinator, renderer, controls/overlays, effects, and native resource ownership.
- Move newly discovered portable decisions to shared before mapping Android to them.
- Exit: host composables do not make persistence/cloud decisions; Android output and behavior remain unchanged.

### Phase 4 — Shared reader-engine modularization

- Split HTML document construction into document, styles, scripts, pagination, annotation, media, and sanitization responsibilities.
- Split shared native pagination/chrome/EPUB engines using the same feature boundaries.
- Preserve generated output where byte compatibility matters and add golden/fixture tests only where necessary.
- Exit: small public entry points, independently testable components, portable source enforcement passing.

### Phase 5 — Legacy duplication and naming cleanup

- Eliminate `Common.kt` into feature-owned files.
- Remove dead contracts/wrappers after proving there are no production consumers.
- Consolidate remaining Android/shared model pairs and explicitly name truly platform-specific types.
- Exit: no speculative production abstractions, no ambiguous platform types, and no new generic dumping grounds.

### Phase 6 — Permanent quality gates and completion audit

- Retain portable import enforcement and add dependency-direction and mega-file ratchet checks.
- Add static analysis/formatting checks only where they are deterministic and configuration-cache-safe.
- Audit every phase requirement against source, focused tests, cross-platform compilation, and git history.
- Exit: all gates run in normal verification, all phases have local `master` commits, and this document contains final metrics and remaining documented exceptions.

## Phase history

| Phase | Status | Android LOC delta | Shared LOC delta | Commit | Verification/result |
| --- | --- | ---: | ---: | --- | --- |
| 0 | Complete (2026-08-11) | 0 | 0 | pending | Baseline, standards, largest-file inventory, safety matrix, and portable-source verification established. |
| 1 | Pending | — | — | — | — |
| 2 | Pending | — | — | — | — |
| 3 | Pending | — | — | — | — |
| 4 | Pending | — | — | — | — |
| 5 | Pending | — | — | — | — |
| 6 | Pending | — | — | — | — |
