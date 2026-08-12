# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## Project

Multi-module Android skeleton. The modules, their dependency wiring, and the build setup exist. `:core:common` is still **empty** — its source sets hold only `.gitkeep`. There is no networking module: `:core:network` was removed, so nothing in this repo reaches the network yet.

Two modules carry one worked example each, built by the skills named next to them, **standing on a seam rather than a faked layer below**:

- `:feature` — the Compose `postlist` screen (`compose-feature-screen` skill). Its ViewModel takes `PostRepository = PostRepository()` and maps `Post → PostUiModel` through an `internal` extension in `PostListUiState.kt` (`wire-feature-to-data` skill). The `try`/`catch` at that call survives only until `:core:common` supplies `Result`.
- `:core:data` — the `Post` domain model and the `PostRepository` contract (`core-data-repository` skill). `PostRepositoryImpl` takes a `suspend () -> List<Post>` seam defaulting to `{ emptyList() }`, because there is no networking layer to call. It returns `List<Post>` rather than `Result` until `:core:common` supplies one.

The two are wired to each other; the only remaining seam is `PostRepositoryImpl.fetchPosts`, which stays open until a networking layer exists. So the screen still renders its empty state — that is the honest end of a real chain, not a missing connection.

```
:app  ──►  :feature  ──►  :core:data  ──►  :core:common
```

- `:app` — manifest, theme, icons. No code of its own; it contributes the `MAIN`/`LAUNCHER` intent-filter once a feature declares an activity.
- `:feature` — Activities, ViewModels, UI state, Compose screens and components.
- `:core:data` — repositories, domain models, mappers, data sources.
- `:core:common` — shared result type, dispatcher provider, extensions.

There is no networking module. Retrofit/OkHttp/Gson aliases remain in `gradle/libs.versions.toml` but nothing declares them; the `INTERNET` and `ACCESS_NETWORK_STATE` permissions left with `:core:network` and are no longer merged into the APK. Adding networking back means a new module plus those permissions in its manifest — not Retrofit dropped into `:core:data`.

Namespaces mirror the path (`:core:data` → `com.sample.demo.core.data`). Dependencies point downward only. Each module's `build.gradle.kts` already declares the libraries its layer is meant to use, so new code should not need new coordinates.

## Commands

`gradlew.bat` (PowerShell) or `./gradlew` (Bash tool). Drop the module prefix to run a task in every module.

```powershell
.\gradlew.bat :app:assembleDebug     # build debug APK (all modules)
.\gradlew.bat testDebugUnitTest      # unit tests, all modules
.\gradlew.bat lintDebug              # lint, all modules
.\gradlew.bat :app:installDebug      # install to connected device
.\gradlew.bat :core:data:testDebugUnitTest --tests "*PostRepositoryImplTest"
.\gradlew.bat :app:connectedDebugAndroidTest   # instrumented, needs a device
```

## Toolchain — do not "modernize" these

AGP 9.3.0, Gradle 9.5, `compileSdk`/`targetSdk` 37, `minSdk` 24. The DSL differs from most Android documentation:

- `compileSdk { version = release(37) }`, not `compileSdk = 37`.
- Minification is `buildTypes.release { optimization { enable = false } }` — there is no `isMinifyEnabled`/`proguardFiles` to edit.
- R8 keep rules go in `app/src/main/keepRules/` (AGP merges the whole directory), not `proguard-rules.pro`.
- No Kotlin Gradle plugin, despite `.kt` sources — AGP 9 has built-in Kotlin support. Adding `org.jetbrains.kotlin.android` is never the fix for a Kotlin compile error here. It is also why Gson is the JSON converter rather than kotlinx.serialization.
- **One exception:** `org.jetbrains.kotlin.plugin.compose` (the Compose compiler plugin) is applied to `:feature`. `buildFeatures { compose = true }` alone fails at configuration time without it. Its version must track the Kotlin version AGP bundles — verified pair: AGP 9.3.1 + compiler plugin 2.4.10 + `compose-bom` 2026.06.01. This is not a licence to add other compiler plugins.
- Configuration cache is on; the daemon is pinned to JVM 25 (foojay-provisioned) while Java source/target is 11; `FAIL_ON_PROJECT_REPOS` means repositories are added in `settings.gradle.kts` only.

Dependencies live only in `gradle/libs.versions.toml` — never hardcode a coordinate. Aliases use dots in Kotlin (`libs.androidx.core.ktx`) where the TOML key uses dashes.

New module: create the directory, add `include(":path")` to `settings.gradle.kts`, copy an existing library `build.gradle.kts`, set a unique namespace. There is no convention plugin or `buildSrc`, so the sdk/`compileOptions` blocks are repeated per module.

## Conventions

These held before the implementation was stripped out; new code should follow them.

- **The three skills are user-invoked only.** `/compose-feature-screen`, `/core-data-repository` and `/wire-feature-to-data` carry the full version of the rules below. All three are configured `disable-model-invocation: true`, so Claude cannot load them on its own: when a request falls in one's scope, name the skill and let the user run it, then follow it. Working without it is allowed but means holding the rules below by hand.
- **`:core:data` hides whatever it fetches from.** Transport types and wire models must not surface in its public API — keep API-facing constructors `internal`, and depend on any future networking module with `implementation`, not `api`. Wire → domain mapping lives in `core/data/mapper/`. Use `/core-data-repository` for repositories, data sources, domain models and mappers — it carries that boundary rule, the layering (domain model → mapper → internal data source → public repository), and the fake-based test patterns. **The three skills still describe a `:core:network` module that no longer exists** — read their remote-data-source sections as the shape to follow *if* networking is added back, not as the current repo.
- **Errors cross boundaries as values.** A `safeApiCall`-style helper folds every outcome into `Result`, classifying failures as `NetworkException`; repositories and ViewModels branch on `Result` instead of catching. Nothing implements this yet — it waits on `:core:common` and a networking layer.
- **Each module declares its own manifest components.** A feature declares its activity without `android:exported`; `:app` merges in the `MAIN`/`LAUNCHER` filter. No module contributes `INTERNET` any more.
- **No DI framework.** Default constructor arguments plus an explicit `ViewModelProvider.Factory`. Adding Hilt/Koin replaces those defaults; it does not restructure the modules.
- **Jetpack Compose (Material 3) for new UI in `:feature`.** Use `/compose-feature-screen` — it carries the layering (Route → stateless Screen → components), the AndroidX component API guidelines, and the build wiring. Compose stays inside `:feature`; `:core:*` must never expose a `@Composable` or a `State`. View binding remains enabled so existing XML screens keep working; do not convert them unasked. Window themes still live in `:app` (`Theme.DemoClaudeAgent.Compose`), while the Compose color scheme comes from `DemoTheme` in `:feature`.
- **One state object per screen**, exposed as a `StateFlow`, collected with `collectAsStateWithLifecycle()` in Compose (or `repeatOnLifecycle(STARTED)` in a View screen). Add a field to it rather than a second stream.
- **Never fabricate a missing layer.** If `:core:data`/`:core:common` do not yet have what a screen needs, build the screen anyway and leave the dependency as a function-type constructor parameter defaulting to "nothing". No stub repository, fake data source, in-memory `Impl`, or invented domain model — those are a data layer in the wrong module, and they get committed and later have to be unwound. When the layer below does land, use `/wire-feature-to-data` to replace the seam — it owns the domain → UI mapper, the `ViewModelProvider.Factory` check, and the boundary rules that wiring tends to break.
- **Shared code splits by whether it is UI.** Reusable `@Composable`s go in `feature/ui/components/`; reusable non-UI functions go in `feature/util/`, which stays free of `androidx.compose.*` and `android.*` so it is testable as plain Kotlin. Compose-aware helpers that emit nothing (`Modifier` extensions, `remember…` helpers) belong under `feature/ui/`, not `util/`.
