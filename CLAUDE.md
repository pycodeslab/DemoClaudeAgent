# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## Project

Multi-module Android skeleton. The modules, their dependency wiring, and the build setup exist; **there is no implementation code** — every source set is empty apart from `:app`'s template `ExampleUnitTest`/`ExampleInstrumentedTest`. Empty package directories are held open with `.gitkeep`.

```
:app  ──►  :feature  ──►  :core:data  ──►  :core:network  ──►  :core:common
```

- `:app` — manifest, theme, icons. No code of its own; it contributes the `MAIN`/`LAUNCHER` intent-filter once a feature declares an activity.
- `:feature` — Activities, ViewModels, UI state, adapters.
- `:core:data` — repositories, domain models, mappers, data sources.
- `:core:network` — Retrofit/OkHttp setup, API interfaces, wire models, error mapping.
- `:core:common` — shared result type, dispatcher provider, extensions.

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
- No Kotlin Gradle plugin anywhere, despite `.kt` sources — AGP 9 has built-in Kotlin support. Adding `org.jetbrains.kotlin.android` is never the fix for a Kotlin compile error here. It also rules out compiler-plugin libraries, which is why Gson is the JSON converter rather than kotlinx.serialization.
- Configuration cache is on; the daemon is pinned to JVM 25 (foojay-provisioned) while Java source/target is 11; `FAIL_ON_PROJECT_REPOS` means repositories are added in `settings.gradle.kts` only.

Dependencies live only in `gradle/libs.versions.toml` — never hardcode a coordinate. Aliases use dots in Kotlin (`libs.androidx.core.ktx`) where the TOML key uses dashes.

New module: create the directory, add `include(":path")` to `settings.gradle.kts`, copy an existing library `build.gradle.kts`, set a unique namespace. There is no convention plugin or `buildSrc`, so the sdk/`compileOptions` blocks are repeated per module.

## Conventions

These held before the implementation was stripped out; new code should follow them.

- **`:core:data` hides `:core:network`** (`implementation`, not `api`). Retrofit types and wire models must not surface in its public API — keep API-facing constructors `internal`. Wire → domain mapping lives in `core/data/mapper/`.
- **Errors cross boundaries as values.** A `safeApiCall` helper folds every outcome into `Result`, classifying failures as `NetworkException`; repositories and ViewModels branch on `Result` instead of catching. Endpoints are `suspend` functions returning `Response<T>` in `core/network/api/`.
- **Each module declares its own manifest components.** A feature declares its activity without `android:exported`; `:app` merges in the `MAIN`/`LAUNCHER` filter. `INTERNET` comes from `:core:network`.
- **No DI framework.** Default constructor arguments plus an explicit `ViewModelProvider.Factory`. Adding Hilt/Koin replaces those defaults; it does not restructure the modules.
- **Views + XML, no Compose** (adding it needs the plugin, BOM, and `buildFeatures.compose`). `:feature` uses view binding, and its layouts reference theme attributes (`?attr/…`) because `Theme.DemoClaudeAgent` lives in `:app`.
- **One state object per screen**, exposed as a `StateFlow` and collected with `repeatOnLifecycle(STARTED)`. Add a field to it rather than a second stream.
