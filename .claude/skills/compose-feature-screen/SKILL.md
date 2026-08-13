---
name: compose-feature-screen
description: Build a screen in the :feature module with Jetpack Compose — UI state, UI logic, reusable UI components, and business logic in a ViewModel. Invoke explicitly with /compose-feature-screen when adding or modifying a screen, Composable component, UiState, UI event, or feature ViewModel in this repo. Takes an optional feature name as argument (`/compose-feature-screen Login`) which becomes the screen name; without it, plain prompting works exactly as before. Encodes the AndroidX Compose component API guidelines plus this repo's AGP 9 / no-KGP toolchain rules.
argument-hint: "[Ticket-Number]"
disable-model-invocation: true
---

# Compose screen in `:feature`

Produces one screen as five separable pieces: **UiState**, **UI events**, **ViewModel (business logic)**, **stateless screen**, **reusable components** — following the [AndroidX Compose component API guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md).

`CLAUDE.md` is already in context and owns the architecture, toolchain, and testing rules — on any conflict it wins. This skill only adds what is Compose-specific.

## Arguments

> Arguments passed: `$ARGUMENTS`

Everything typed after `/compose-feature-screen` is interpolated into the quoted line above,
verbatim — that line is the single source of truth for what the user passed; the rest of this
section only says how to read it. **Empty backticks there mean no argument was given**, which is
the ordinary case and not an error: go to step 2 of "Resolving the name" and behave exactly as this
skill did before the argument existed.

`argument-hint` advertises the shape as `[FeatureName] [deskripsi screen bebas]` — both halves
optional and independent:

```
/compose-feature-screen                                     # nothing passed — usual behaviour
/compose-feature-screen Login                               # name only
/compose-feature-screen post-list                           # any casing, normalised below
/compose-feature-screen Login pakai email dan password      # name + free-form prompt
/compose-feature-screen "User Profile" tampilkan avatar     # quote a multi-word name
/compose-feature-screen buatkan screen login dengan OTP     # no name — all of it is prompt
```

### Splitting the argument

It arrives as one string, so split it yourself. It carries a name **only when it starts with one**:

- Starts with a quoted string → that is the name, the rest is prompt.
- Otherwise the first whitespace-separated token is the name only if it reads as an identifier:
  one word, letters/digits/`-`/`_`, nothing else.
- An ordinary request word in that position (`buatkan`, `bikin`, `tambahkan`, `add`, `create`,
  `make`, `screen`, `halaman`, `a`, `the`) is **not** a name — the whole string is prompt.
- Everything not consumed as the name is the prompt, and it is a full instruction, not a footnote:
  read it for layout, fields, states and behaviour just as you would a normal request.

So the last two examples split differently: `"User Profile"` is a name, `buatkan` is not. If a bare
first token is genuinely ambiguous, prefer reading it as prose and confirm the screen name in your
first sentence rather than silently naming a package after it.

### Resolving the name

1. **The argument starts with a name** → that is the name. It wins over a name guessed from the
   prose, so `/compose-feature-screen Login buatkan halaman profil` builds `Login` — but when the
   prose contradicts the argument that plainly, say which one you used in your first sentence.
2. **No name in it** (empty, or prose only) → take the name from the request as usual
   (`"buatkan screen login"` → `Login`). This is the pre-existing behaviour and stays fully
   supported.
3. **Neither** → ask for the name before writing files. Do not invent one, and do not fall back to
   `Main`, `Home` or `Sample` — a wrongly named screen means renaming a package, every file in it
   and a manifest entry later.

### Normalising the name

One name yields three forms, and every template placeholder is one of them:

| Form | Rule | `post-list` | `Login` | `"user profile"` |
| --- | --- | --- | --- | --- |
| `<Screen>` — classes, files | PascalCase, separators dropped | `PostList` | `Login` | `UserProfile` |
| `<screen>` — package segment | all lowercase, separators dropped | `postlist` | `login` | `userprofile` |
| Activity entry | `<Screen>Activity` | `PostListActivity` | `LoginActivity` | `UserProfileActivity` |

Accept `PascalCase`, `camelCase`, `kebab-case`, `snake_case` and quoted multi-word input; they all
normalise to the same thing. Strip a trailing `Screen`/`Activity`/`Feature` the user typed
(`LoginScreen` → `Login`) so the templates do not produce `LoginScreenScreen.kt`. The package
segment is lowercase with no underscore because that is what this repo already uses —
`com.sample.demo.feature.postlist`, not `post_list`.

Then substitute those forms into §"File layout" and into `references/templates.md`, whose `PostList`
examples are placeholders for `<Screen>`.

**Before creating anything, check whether that screen already exists** under
`feature/src/main/java/com/sample/demo/feature/<screen>/`. If it does, modify it — a name in the
argument is not an instruction to create a second copy.

## Before you write code

1. **Check which layers below exist — then build the screen either way.** A screen never blocks on `:core:data`/`:core:common`. See "Missing layers" below: you build the full screen and fabricate nothing.
2. **Confirm Compose is enabled** in the target module — `buildFeatures { compose = true }` *and* the `compose-compiler` plugin alias. See `references/toolchain.md`. Without the plugin the build fails at configuration time.
3. **Do not create a theme.** Theming is not this skill's job. Look for a theme wrapper the module
   already owns (conventionally `feature/src/main/java/com/sample/demo/feature/ui/theme/`) and use
   it; when there is none, wrap in `MaterialTheme` directly and leave it at that. Never write a
   `Theme.kt`, a color palette or a typography set as a side effect of building a screen — if the
   project wants a shared theme, that is its own task, asked for on its own.

## File layout

One package per screen under `com.sample.demo.feature.<screen>`, plus a shared `ui/` package.
`<Screen>` and `<screen>` are the two normalised forms from §"Argument" — for `/compose-feature-screen Login`
this layout reads `login/LoginRoute.kt`, `login/LoginViewModel.kt`, and so on:

```
feature/src/main/java/com/sample/demo/feature/
├── ui/components/                     # @Composable components reused across screens
├── util/                              # shared NON-UI functions (see below)
└── <screen>/
    ├── <Screen>Activity.kt            # setContent { MaterialTheme { <Screen>Route() } }
    ├── <Screen>Route.kt               # stateful: owns ViewModel, collects state
    ├── <Screen>Screen.kt              # stateless: (uiState, onEvent) -> Unit
    ├── <Screen>UiState.kt             # the state object — and nothing else
    ├── <Screen>Event.kt               # sealed interface: UI -> ViewModel
    ├── <Screen>Effect.kt              # sealed interface: one-time ViewModel -> UI  (only if any)
    ├── <Screen>UiModel.kt             # the model(s) this screen renders            (only if any)
    ├── <Screen>ViewModel.kt           # business logic + ViewModelProvider.Factory
    └── components/                    # components private to this screen
```

**One declaration per file, each file named after it.** `<Screen>UiState.kt` holds the state object
alone — the events, the effects and the UI models get their own files even though Kotlin would
happily take them all in one. They change for different reasons and get read for different reasons:
the events are the screen's whole interaction surface, the UI model is what the `wire-feature-to-data`
mapper targets, and burying either in the state file is what makes them get missed.

`<Screen>Effect.kt` and `<Screen>UiModel.kt` are conditional, not optional — write them when the
screen has a one-time effect or renders a model of its own, and do not create empty placeholders
when it has neither.

`feature/src/test/java/com/sample/demo/feature/<screen>/<Screen>ViewModelTest.kt` is not optional — the ViewModel is where the logic worth testing lives.

## The five pieces

**UiState** — its own file, holding the state object alone. Add a field rather than a second stream. Model mutually exclusive conditions as fields the UI can branch on (`isLoading`, `errorMessage`, `items`); prefer derived `val` over duplicated truth. Expose it as `MutableStateFlow(...).asStateFlow()`, not `stateIn` — the ViewModel owns the state rather than deriving it from an upstream flow, and the tests read `uiState.value` with no collector attached. The UI models it holds live in `<Screen>UiModel.kt`, not here.

**Events** — `<Screen>Event.kt`: a `sealed interface <Screen>Event` with one `data class`/`data object` per user intent, and nothing else in the file. The stateless screen receives a single `onEvent: (<Screen>Event) -> Unit`, so adding an interaction never changes a signature. That is UI → ViewModel. The other direction — a one-time effect the ViewModel fires at the UI (navigate, toast, snackbar) — never goes in `UiState`, because state replays on every recomposition and a navigation would fire twice. It is a `sealed interface <Screen>Effect` in its own `<Screen>Effect.kt`, delivered over a `Channel(Channel.BUFFERED).receiveAsFlow()` and collected in the Route inside `LaunchedEffect(Unit)`. Only write that file when the screen actually has such an effect.

**ViewModel** — where validation, retry, ordering and domain → UI mapping live. Exposes `uiState: StateFlow<…>` and a `companion object` `ViewModelProvider.Factory`. Its dependency arrives as a constructor parameter with a default (no DI framework — see `CLAUDE.md`). When `:core:data` exists that parameter is the repository — use the `wire-feature-to-data` skill, which owns that join; when it does not, see "Missing layers" below.

**Stateless screen** — `@Composable fun <Screen>Screen(uiState: …, onEvent: (…) -> Unit, modifier: Modifier = Modifier)`. No ViewModel reference, no `remember` of business state. This is what previews and screenshot tests use.

**Route** — the thin stateful wrapper: obtains the ViewModel, collects with `collectAsStateWithLifecycle()`, delegates to the stateless screen. Nothing else.

**Components** — every rule in `references//c.md` applies. The ones most often broken: `modifier: Modifier = Modifier` is the first optional parameter and is applied once, to the root-most layout; no `MutableState` parameters; defaults live in a public `<Component>Defaults` object; prefer `@Composable` content slots over `String`/`ImageBitmap` parameters.

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
- Own UI models (`<Screen>UiModel`, in `<Screen>UiModel.kt`) in `:feature`. Those are a UI concern, not a fabricated domain — but they carry only what this screen renders.
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

- **Compose only inside `:feature`.** `:core:*` stays Compose-free — a repository must never expose a `@Composable` or a `State`.
- **No theme files.** This skill never writes a `Theme.kt`, color scheme or typography set. Use the module's existing theme wrapper if it has one, otherwise `MaterialTheme` directly.
- **Compose artifacts get their version from the BOM** — no `version.ref` on `compose-*` entries in `gradle/libs.versions.toml`.
- **AGP 9 DSL** — `compileSdk { version = release(37) }`, `optimization { enable = false }`. Do not "modernize".
- **Adding `org.jetbrains.kotlin.android` is never the fix** for a Kotlin error here. AGP 9 supplies Kotlin. The *only* Kotlin plugin in this build is the Compose compiler plugin, and its version must track AGP's bundled Kotlin (`references/toolchain.md`).
- **The Activity carries no `android:exported`** — `:app` merges the `MAIN`/`LAUNCHER` filter and owns that attribute.
- Do not rip out existing XML screens unless asked.

## Verification — required, and show the output

```powershell
.\gradlew.bat :feature:testDebugUnitTest        # ViewModel logic
.\gradlew.bat :feature:lintDebug                # Compose lint rules run here
.\gradlew.bat :app:assembleDebug                # whole chain still links
```

Compose UI tests (`createComposeRule`) live in `androidTest` and need a device:
`.\gradlew.bat :feature:connectedDebugAndroidTest`. Write them when the screen has real interaction; do not claim they passed if no device was attached.

Report the commands' actual output. "Should work" is not verification.
