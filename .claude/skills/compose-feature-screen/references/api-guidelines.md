# Compose component API guidelines — working subset

Distilled from
<https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>.
That document governs androidx components; everything below is the part that applies to
feature-level components in this repo. When the two disagree, the upstream document wins —
re-read it rather than guessing.

## Naming

- Components are `PascalCase` nouns, return `Unit`, and emit UI. `PostCard`, not `drawPostCard`.
- `Basic*` prefix = barebones, undecorated, expects the caller to style it (`BasicTextField`).
  Unprefixed = design-system decorated and ready to use.
- No company/module prefixes (`DemoButton`). Use domain names (`PostCard`) or spec names
  (`OutlinedButton`, `ContainedButton`). The most-used variant gets the unprefixed name.
- A `@Composable` that returns a value is not a component; name it like a normal function.

## One component, one problem

- Each component solves exactly one problem. A component doing both click handling and
  selection state is two components.
- Build the low-level, single-purpose block first; add the opinionated, fewer-knobs wrapper on
  top of it.
- Before adding a component, try composing it from existing building blocks. If that is easy,
  the component is not needed.
- **Component or `Modifier`?** Distinct UI or a change in the composable hierarchy → component.
  Behaviour that could apply to any single component → `Modifier`. Never write a `Padding()`
  component wrapping children.

## The `modifier` parameter

Every UI-emitting component takes one, and only one:

```kotlin
@Composable
fun PostCard(
    title: String,                       // required data first
    modifier: Modifier = Modifier,       // first optional parameter
    subtitle: String? = null,            // remaining optional parameters
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,   // trailing slot last
)
```

- Type `Modifier`, default `Modifier` — never `Modifier.padding(8.dp)` as a default.
- Applied **once**, as the **first** element of the chain, to the **root-most** layout:
  `Column(modifier = modifier.padding(16.dp))`. Never `Column(modifier = Modifier.padding(16.dp).then(modifier))`
  and never forwarded to an inner child.
- Never multiple modifier parameters (`rowModifier`, `iconModifier`). If a caller needs to
  modify an inner part, that inner part wants to be a slot.
- Required (no default) only if the component has no default size of its own.
- Do not add optional parameters for behaviour a `Modifier` already provides
  (`onClick` on a plain container, `clipToCircle`, padding).

## Parameter order

`required` → `modifier: Modifier = Modifier` → `optional` → trailing `@Composable` lambda.
Within each group put data before metadata/customization, and keep semantically related
parameters adjacent (all colors together).

## State

- **Never take `MutableState<T>`.** It splits ownership of the state. Take
  `value: T` + `onValueChange: (T) -> Unit`, or a domain `ComponentState` class.
- **Never take `State<T>`.** Take the plain value, or `() -> T` when reading must be deferred
  to a later phase.
- State is hoisted to the caller. A screen-level component is stateless and gets its state as
  parameters — that is what makes previews and screenshot tests possible.
- `remember` inside a component is for UI-only, disposable state (scroll position, expanded
  flag). Business state belongs to the ViewModel.

## Slots

- Prefer `@Composable` lambda slots over `String`/`ImageBitmap`/`Int`-resource parameters.
  Slots avoid a combinatorial explosion of overloads and let callers style freely.
- Give slots a layout scope receiver when the position is meaningful:
  `content: @Composable ColumnScope.() -> Unit`.
- Offer a single-`content`-slot overload for layout flexibility where it makes sense.
- Slot lifecycle follows the parent. If an internal `if/else` would restructure the hierarchy
  and dispose slot content, use `movableContentOf`.
- Avoid DSL-based slots; they are justified only for laziness (`LazyColumn`'s `item`/`items`).

## Defaults

- Every default expression must be **public** — a caller wrapping the component has to be able
  to reproduce it. No `private val DEFAULT_PADDING`.
- Group defaults in a public `<Component>Defaults` object:
  ```kotlin
  object PostCardDefaults {
      val ContentPadding: Dp = 16.dp
      @Composable fun colors(): CardColors = CardDefaults.cardColors()
  }
  ```
- Read `CompositionLocal`s (theme, typography) **in the default expression**, so callers can
  override — never deep in the implementation, which makes opting out impossible.
- No grab-bag `ComponentStyle`/`ComponentConfiguration` parameter objects. Separate,
  semantically named composables instead (`PrimaryButton` / `SecondaryButton`).
- `null` means "absent", not "use the default". If a parameter has a sensible default value,
  give it that value, not `null`.

## Accessibility

- Merge child semantics for a component that reads as one unit:
  `Modifier.semantics(mergeDescendants = true) {}`.
- Leaf components that carry meaning take the relevant parameter (`contentDescription` on an
  image). Do not add a parameter for every conceivable semantic — the `modifier` parameter
  already lets callers attach their own `semantics`.
- Decorative imagery takes `contentDescription = null` deliberately, not by omission.
- Imitate the semantics of the closest existing Compose component.

## Previews and testing

- Previews must render the initial state without `LaunchedEffect` or async work. Feed them
  hand-written state objects, never a real ViewModel.
- Use `LocalInspectionMode.current` sparingly, only to work around something that genuinely
  cannot render in the preview host.
- Screenshot- and UI-testability is a design property: it comes from the screen being stateless
  with state passed in.

## API evolution

Do not remove or reorder existing parameters. Add new ones with a default, positioned last or
immediately before the trailing lambdas. For a source-incompatible change, add the new overload
and deprecate the old one with `DeprecationLevel.HIDDEN`, delegating to the new one.

## Review checklist

Run this over every public Composable before declaring the work done:

- [ ] `PascalCase`, returns `Unit`, named for the thing it shows
- [ ] exactly one `modifier: Modifier = Modifier`, first optional param, applied once to the root
- [ ] parameter order: required → modifier → optional → trailing lambda
- [ ] no `MutableState`/`State` parameters; state hoisted to the caller
- [ ] slots instead of `String`/resource parameters where content should be free-form
- [ ] defaults public, grouped in `<Component>Defaults`, `CompositionLocal` read in the default
- [ ] `null` only ever means absent
- [ ] `contentDescription` decided deliberately; semantics merged where the component reads as one unit
- [ ] a `@Preview` exists and renders from a literal state object
- [ ] the screen-level composable is stateless and free of ViewModel references
