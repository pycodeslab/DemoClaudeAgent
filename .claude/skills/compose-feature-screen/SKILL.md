---
name: compose-feature-screen
description: Build a screen in the :feature module with Jetpack Compose — UI state, UI logic, reusable UI components, and business logic in a ViewModel. Invoke explicitly with /compose-feature-screen when adding or modifying a screen, Composable component, UiState, UI event, or feature ViewModel in this repo. Encodes the AndroidX Compose component API guidelines plus this repo's AGP 9 / no-KGP toolchain rules.
disable-model-invocation: true
---

# Compose screen in `:feature`

Produces one screen as five separable pieces: **UiState**, **UI events**, **ViewModel (business logic)**, **stateless screen**, **reusable components** — following the [AndroidX Compose component API guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md).

Read `@CLAUDE.md` first — it owns the architecture and toolchain rules. This skill only adds what is Compose-specific.

## Before you write code

1. **Check which layers below exist — then build the screen either way.** A screen never blocks on `:core:data`/`:core:common`. See "Missing layers" below: you build the full screen and fabricate nothing.
2. **Confirm Compose is enabled** in the target module — `buildFeatures { compose = true }` *and* the `compose-compiler` plugin alias. See `references/toolchain.md`. Without the plugin the build fails at configuration time.
3. **Check for an existing theme** at `feature/src/main/java/com/sample/demo/feature/ui/theme/`. Create it once (template in `references/templates.md`); never duplicate per screen.

## File layout

One package per screen under `com.sample.demo.feature.<screen>`, plus a shared `ui/` package:

```
feature/src/main/java/com/sample/demo/feature/
├── ui/theme/Theme.kt                  # created once, shared
├── ui/components/                     # @Composable components reused across screens
├── util/                              # shared NON-UI functions (see below)
└── <screen>/
    ├── <Screen>Activity.kt            # setContent { DemoTheme { <Screen>Route() } }
    ├── <Screen>Route.kt               # stateful: owns ViewModel, collects state
    ├── <Screen>Screen.kt              # stateless: (uiState, onEvent) -> Unit
    ├── <Screen>UiState.kt             # one state object + sealed event interface
    ├── <Screen>ViewModel.kt           # business logic + ViewModelProvider.Factory
    └── components/                    # components private to this screen
```

`feature/src/test/java/com/sample/demo/feature/<screen>/<Screen>ViewModelTest.kt` is not optional — the ViewModel is where the logic worth testing lives.

## The five pieces

**UiState** — one immutable `data class` per screen, exposed as `StateFlow`. Add a field rather than a second stream. Model mutually exclusive conditions as fields the UI can branch on (`isLoading`, `errorMessage`, `items`); prefer derived `val` over duplicated truth.

**Events** — a `sealed interface <Screen>Event` with one `data class`/`data object` per user intent. The stateless screen receives a single `onEvent: (<Screen>Event) -> Unit`, so adding an interaction never changes a signature.

**ViewModel** — the *only* place with business logic: validation, retry, ordering, mapping domain → UI. Exposes `uiState: StateFlow<…>` and a `companion object` `ViewModelProvider.Factory`. Its dependency arrives as a constructor parameter with a default (no DI framework — see `@CLAUDE.md`). When `:core:data` exists that parameter is the repository — use the `wire-feature-to-data` skill, which owns that join; when it does not, see "Missing layers" below.

**Stateless screen** — `@Composable fun <Screen>Screen(uiState: …, onEvent: (…) -> Unit, modifier: Modifier = Modifier)`. No ViewModel reference, no `remember` of business state. This is what previews and screenshot tests use.

**Route** — the thin stateful wrapper: obtains the ViewModel, collects with `collectAsStateWithLifecycle()`, delegates to the stateless screen. Nothing else.

**Components** — every rule in `references/api-guidelines.md` applies. The ones most often broken: `modifier: Modifier = Modifier` is the first optional parameter and is applied once, to the root-most layout; no `MutableState` parameters; defaults live in a public `<Component>Defaults` object; prefer `@Composable` content slots over `String`/`ImageBitmap` parameters.

Load `references/templates.md` for the code skeletons and `references/api-guidelines.md` before writing or reviewing any public Composable.

## Missing layers: build the screen, fabricate nothing

A screen must stand on its own. If the data or domain layer it would eventually use does not exist yet, **do not create one** — no stub repository, no fake data source, no in-memory `Impl`, no invented domain model, no placeholder API. Inventing them produces code that looks finished, gets committed, and then has to be found and unwound later.

Build every part that is genuinely a `:feature` concern — UiState, events, ViewModel, Screen, components, tests — and leave the data dependency as a **seam**: a constructor parameter whose type is a function, defaulting to "nothing to load".

```kotlin
class PostListViewModel(
    // :core:data does not exist yet, so there is nothing to load and nothing is provided.
    // When PostRepository lands, change this default to call it and delete the try/catch.
    private val loadPosts: suspend () -> List<PostUiModel> = { emptyList() },
) : ViewModel()
```

Why a function type rather than an interface you declare here: an interface plus an implementation *is* a data layer, just one in the wrong module. A function type adds no types to the module, keeps the whole state machine reachable in tests (they pass a real lambda), and collapses to one line when the repository arrives.

Rules for this case:

- The default supplies **no data** — `{ emptyList() }`, not sample content. An empty screen is the honest result of having no data layer.
- Own UI models (`<Screen>UiModel`) in `:feature`. Those are a UI concern, not a fabricated domain — but they carry only what this screen renders.
- Without `:core:common` there is no `Result`, so failures arrive as thrown exceptions and the ViewModel catches at that single seam. Note in a comment that this reverts to branching on `Result` once `:core:common` exists.
- When the repository later lands, replacing this seam is the `wire-feature-to-data` skill's job — including the domain → UI mapper. Do not improvise it here.
- **Say so in your summary.** Report which layers were absent and what was therefore not built. Never let missing layers show up as a silent gap.
- Do not add dependencies to `:feature`'s `build.gradle.kts` to compensate. Retrofit, Gson and OkHttp are deliberately absent from this module.

## Where shared code goes

Two destinations, and the test is whether the thing *is UI*:

| Kind | Goes in | Rule |
| --- | --- | --- |
| Reusable `@Composable` that emits UI | `ui/components/` | Follows `references/api-guidelines.md` in full |
| Reusable function that does **not** emit UI | `util/` | Pure Kotlin — no `@Composable`, no `androidx.compose.*`, no `android.*`, no `Context`, no `View` |
| Compose-aware helper that emits nothing (`Modifier` extension, `remember…` helper) | `ui/` | Not `util/` — it depends on Compose, so it does not belong with the framework-free code |

`util/` is for logic several screens share: query matching, formatting, validation, sorting. Keep each function pure and top-level so it is directly unit-testable without a ViewModel, a device, or a Compose rule — that testability is the whole reason the split exists.

The same promotion rule applies to `@Composable`s, and it is the one this repo already trips over:
`ErrorState` and `EmptyState` name no screen's types, so the moment a second screen needs them they
move from `<screen>/components/` to `ui/components/` — leave the old copy behind and you get two
that drift apart. A promoted component must also stop carrying one screen's wording: a default like
`message: String = "Belum ada post."` becomes `"Belum ada data."`, with each screen passing its own.
Writing a generic component straight into `ui/components/` is fine; writing a second copy of one is
not.

Do not pre-create `util/` speculatively. A function earns its place there when a second screen needs it, **or** when it is already fully general — generic, with no screen-specific types, like `filterByQuery` in `references/templates.md`. A helper written against one screen's models stays private to that screen until a second caller appears.

## Non-negotiables in this repo

- **Compose only inside `:feature`** (and the theme it owns). `:core:*` stays Compose-free — a repository must never expose a `@Composable` or a `State`.
- **No new coordinates outside `gradle/libs.versions.toml`**, and Compose artifacts get their version from the BOM (no `version.ref` on `compose-*` entries).
- **AGP 9 DSL** — `compileSdk { version = release(37) }`, `optimization { enable = false }`. Do not "modernize".
- **Adding `org.jetbrains.kotlin.android` is never the fix** for a Kotlin error here. AGP 9 supplies Kotlin. The *only* Kotlin plugin in this build is the Compose compiler plugin, and its version must track AGP's bundled Kotlin (`references/toolchain.md`).
- **The Activity is declared by `:feature`** without `android:exported`; `:app` merges the `MAIN`/`LAUNCHER` filter.
- Views/XML and Compose coexist here (`viewBinding` stays on). Do not rip out existing XML screens unless asked.

## Verification — required, and show the output

```powershell
.\gradlew.bat :feature:testDebugUnitTest        # ViewModel logic
.\gradlew.bat :feature:lintDebug                # Compose lint rules run here
.\gradlew.bat :app:assembleDebug                # whole chain still links
```

Compose UI tests (`createComposeRule`) live in `androidTest` and need a device:
`.\gradlew.bat :feature:connectedDebugAndroidTest`. Write them when the screen has real interaction; do not claim they passed if no device was attached.

Report the commands' actual output. "Should work" is not verification.
