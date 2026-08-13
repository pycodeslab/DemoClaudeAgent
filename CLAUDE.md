# DemoClaudeAgent

## Module structure & dependency rules

- `:app` — entry point only: manifest, resources, theme. No business logic
- `:feature` — UI (Compose + XML/ViewBinding) + ViewModel
- `:core:data` — repository, data sources, mappers. Owns the public contract `:feature` consumes
- `:core:common` — pure Kotlin utilities, **zero `android.*` imports**

Dependency direction: `:app` → `:feature` → `:core:data` → `:core:common`.
`:app` also declares direct dependencies on all three (see `app/build.gradle.kts`), but only for
DI wiring — never call a repository from `:app`.

- `:feature` must NOT contain Repository or DataSource implementations — ViewModel + UI only
- `:feature` reaches the data layer only through interfaces exposed by `:core:data`
- `:core:data` never depends on `:feature`; `:core:common` depends on nothing internal
- Logic shared between *screens* goes into `feature/util/` (pure Kotlin, no Compose, no `android.*`) —
  see `QueryFilterTest`. Promote it to `:core:common` once a second *module* needs it. Either way,
  never a second feature module

### Current state of the repo

`core/data` and `core/common` hold only `.gitkeep` — no source yet. `feature/src/main` is likewise
empty, but `feature/src/test/` already contains tests for code that does not exist
(`PostListViewModelTest`, `QueryFilterTest`, `PostListScreenTest`). **Those tests are the spec** —
class names, function signatures, and expected behaviour come from them. Read them before writing
the implementation, and do not edit a test to make it match new code.

`app/src/main/AndroidManifest.xml` already registers `com.sample.demo.feature.postlist.PostListActivity`
as the launcher activity, so that class is expected to exist in `:feature`.

## Toolchain

- Java 11 (`JavaVersion.VERSION_11` in every module) — not 17
- compileSdk 37, targetSdk 37, minSdk 24, AGP 9.3.1
- Gradle wrapper only (`./gradlew`), never a globally installed Gradle
- All dependencies come from the version catalog `gradle/libs.versions.toml` — no hardcoded
  coordinates in a module's `build.gradle.kts`
- Configuration cache is on (`org.gradle.configuration-cache=true`); a build script that reads
  mutable state at execution time will fail
- No lint/format plugin is configured (no ktlint, no detekt) — style below is enforced by review

## Build & test commands

```
./gradlew projects                       # list modules
./gradlew build                          # build everything
./gradlew :feature:assembleDebug         # build one module
./gradlew :feature:dependencies          # dependency graph for one module

./gradlew testDebugUnitTest              # all unit tests (fast, no device)
./gradlew :feature:testDebugUnitTest     # unit tests for one module
./gradlew :feature:testDebugUnitTest --tests "com.sample.demo.feature.postlist.PostListViewModelTest"
./gradlew connectedDebugAndroidTest      # instrumentation tests (needs emulator/device)
```

On Windows the wrapper is `.\gradlew.bat` — the skills use that form. Same tasks either way.

Run unit tests before instrumentation tests — they're faster and catch most issues.
`./gradlew testDebugUnitTest` must be green before requesting review.

## Architecture

MVVM, unidirectional data flow: UI observes `StateFlow` from the ViewModel, the ViewModel calls the
repository, the repository is the single source of truth.

### No `:domain` module

Deliberate, not an oversight. Logic that would be a UseCase lives in the ViewModel, or in the
repository when it's a data transformation. Revisit only if one use case is needed by 3+ ViewModels
— and then extract a class inside `:feature`, not a new module.

### State

- One `StateFlow<UiState>` per screen. Never several competing flows on one ViewModel
- One-time events (navigation, toast, snackbar) go through a separate `Channel`/`SharedFlow`,
  never inside `UiState`
- Expose state the ViewModel owns as `MutableStateFlow(...).asStateFlow()` — that is the pattern the
  existing tests read (`viewModel.uiState.value`, no collector attached). Reserve
  `stateIn(..., SharingStarted.WhileSubscribed(5_000), ...)` for state *derived* from an upstream
  flow, where there is something to stop collecting

### Data layer

- The repository is the only layer allowed to choose remote vs local (cache-first, network-first…).
  ViewModels never talk to a DataSource
- Repository functions return `Result<T>` — never throw across the repository boundary. When
  `:core:common` lands it, the variants are `Result.Success(data)` and `Result.Failure(exception)`
  (not `Result.Error`) — this file is the arbiter, so the skills stay consistent with each other
- ViewModels never expose a raw exception to the UI. Today they map it to an `errorMessage: String?`
  field on `UiState` — that is what the existing tests assert. Introduce a sealed `UiError` only when
  the UI has to branch on the *kind* of failure, not just render it
- No offline sync engine, no conflict resolution — out of scope

### DI and navigation

- No DI framework in this project. Wire dependencies with constructor injection, assembled in
  `:app`. Don't introduce Hilt/Koin without asking
- Navigation is activity-per-feature: `:feature` declares its own activities, `:app` picks the
  launcher one in its manifest. There is no shared NavHost

## Code style

### Naming

- ViewModel functions triggering a side effect are prefixed `on`: `onSaveClicked()`, not
  `handleSaveClick()`
- Repository interfaces live in `:core:data` and take no `I` prefix (`PostRepository`, not
  `IPostRepository`); implementations are suffixed `Impl`
- Data source classes are suffixed `DataSource` (`PostRemoteDataSource`, `PostLocalDataSource`)

### Formatting

- Max line length 120
- Trailing commas required in multi-line calls and parameter lists
- No wildcard imports, including `androidx.compose.*`

### Compose

- One composable per file, file named after it — `PostListScreen.kt` holds `PostListScreen` plus its
  private helpers
- Previews are suffixed `Preview`, annotated `@Preview`, and kept in the same file (no `*Previews.kt`)

## Testing

### What's available

- JUnit4 — `org.junit.Test`, never `org.junit.jupiter`
- `kotlinx-coroutines-test` (`runTest`, `UnconfinedTestDispatcher`)
- Assertions from `org.junit.Assert` (`assertEquals`, `assertTrue`, …)
- **No mocking library, and none may be added.** Write fakes by hand — see the existing
  `FakePostRepository` in `feature/src/test/java/com/sample/demo/feature/postlist/PostListViewModelTest.kt`
- `MainDispatcherRule` lives in `feature/src/test/java/com/sample/demo/feature/MainDispatcherRule.kt`
  and swaps `Dispatchers.Main` so `viewModelScope` runs inside `runTest`

### Conventions

- Naming: `PostListViewModel.kt` → `PostListViewModelTest.kt`
- Unit tests go in `src/test/`; Compose/UI tests in `src/androidTest/` using `createComposeRule()`
- Repository tests use in-memory fake data sources — never real network or database
- Extension functions in `:core:common` get a dedicated test file (`StringExt.kt` → `StringExtTest.kt`)
- Cover the branches that matter (success / error / loading), not just the happy path.
  `:core:common` utilities are small and pure — aim for full coverage. No coverage expectation on
  Compose UI itself

## Git etiquette

- Branch: `<type>/<ticket-id>-<short-description>` with type in `feature|fix|refactor|chore`,
  e.g. `feature/PROJ-123-post-list-screen`. Branch off `develop`
- Commits: Conventional Commits with the module as scope —
  `feat(feature): add post list screen`, `fix(core-data): handle empty response`
- Keep a commit scoped to one module unless the change is cohesive end-to-end. History is preserved
  as-is on merge, so each commit should already be meaningful
- PR title `[PROJ-123] Short description`; body states what changed and why, modules affected,
  testing done, plus a screenshot for UI changes. Link the ticket
- Don't bundle unrelated module changes in one PR — split them
- Rebase on `develop`, don't merge it into your branch. Merge commit, not squash
- Never force-push `develop` or `main`

## Gotchas

- `WhileSubscribed(5_000)` means collection stops 5s after the UI stops observing. A network call
  re-firing after rotation is expected, not a bug — but this only applies where `stateIn` is
  actually used (see State above); a plain `asStateFlow()` never stops
- `LaunchedEffect(Unit)` for one-time init: adding a key makes it re-run on recomposition and
  duplicates the call
- `:feature` has both `viewBinding` and `compose` enabled — check which one a screen uses before
  assuming
- Because `:core:data` and `:core:common` are still empty, a new feature usually means writing the
  layer below it first. Write it in the module it belongs to, never as a shortcut inside `:feature`
