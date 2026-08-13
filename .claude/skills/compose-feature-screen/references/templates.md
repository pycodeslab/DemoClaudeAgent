# Templates

Skeletons for the five pieces. `PostList`/`postlist` is a placeholder — replace it with the two
normalised forms of the screen name (`SKILL.md` §"Argument"): `PostList` → `<Screen>` for classes
and files, `postlist` → `<screen>` for the package segment. Invoked as
`/compose-feature-screen Login`, every `PostList` below becomes `Login` and every `postlist`
becomes `login`.

These are shapes to follow, not code to paste unchanged — delete what a given screen does not need.

## On the theme

There is no theme template here, deliberately. This skill does not create one: use the theme
wrapper the module already owns if it has one, and otherwise wrap in `MaterialTheme` directly, as
every example below does. Writing a `Theme.kt`, a color palette or a typography set is a separate
task that has to be asked for on its own.

## 1. UiState — `PostListUiState.kt`

The state object alone. Events, effects and UI models each get their own file.

```kotlin
package com.sample.demo.feature.postlist

data class PostListUiState(
    val isLoading: Boolean = false,
    // A UI model owned by this screen — never a `:core:data` domain model, even once one exists.
    val posts: List<PostUiModel> = emptyList(),
    val query: String = "",
    val errorMessage: String? = null,
) {
    // Derived — never a second source of truth, never a second StateFlow.
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && posts.isEmpty()
}
```

Rules: immutable `data class`; add a field rather than a second stream; derived values are
`val ... get()`, not stored copies.

## 2. Events — `PostListEvent.kt`

UI → ViewModel. One file, one sealed hierarchy, nothing else in it: this is the screen's entire
interaction surface, and it is read on its own far more often than the state is.

```kotlin
package com.sample.demo.feature.postlist

sealed interface PostListEvent {
    data object Refresh : PostListEvent
    data object ErrorDismissed : PostListEvent
    data class QueryChanged(val query: String) : PostListEvent
    data class PostClicked(val postId: Long) : PostListEvent
}
```

## 3. One-time effects — `PostListEffect.kt`

ViewModel → UI, fired once. Write this file **only when the screen has such an effect** — a
navigation, a toast, a snackbar. Do not create an empty one.

```kotlin
package com.sample.demo.feature.postlist

sealed interface PostListEffect {
    data class NavigateToDetail(val postId: Long) : PostListEffect
}
```

It never belongs in `PostListUiState`: state replays on every recomposition, so a navigation held
there would fire twice. The ViewModel exposes it as a channel, and only the Route collects it:

```kotlin
private val _effects = Channel<PostListEffect>(Channel.BUFFERED)
val effects: Flow<PostListEffect> = _effects.receiveAsFlow()
```

## 4. UI model — `PostListUiModel.kt`

What this screen renders, carrying only the fields it renders. Own it in `:feature`; it is a UI
concern, not a domain model, and it does not change shape when `:core:data` later lands. Write this
file only when the screen actually renders a model of its own.

```kotlin
package com.sample.demo.feature.postlist

data class PostUiModel(
    val id: Long,
    val title: String,
    val excerpt: String,
)
```

This type is what the `wire-feature-to-data` mapper targets — which is the reason it gets a file
of its own rather than being buried at the bottom of the state file.

## 5. ViewModel — the only place with business logic

Pick the variant that matches what actually exists. **5a is the current state of this repo.**

### 5a. `:core:data` does not exist yet — provide nothing

The dependency is a function type defaulting to "nothing to load". No stub repository, no fake
source, no sample content. See "Missing layers" in `SKILL.md` for why.

```kotlin
class PostListViewModel(
    // :core:data does not exist yet, so nothing is provided. When PostRepository lands,
    // change this default to call it, and replace the try/catch with a `when (result)`.
    private val loadPosts: suspend () -> List<PostUiModel> = { emptyList() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostListUiState())
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()

    private var loadedPosts: List<PostUiModel> = emptyList()

    init { load() }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // try/catch only because :core:common has no Result yet. Delete it when it does.
            try {
                loadedPosts = loadPosts()
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        posts = loadedPosts.filterByQuery(state.query) { it.title },
                    )
                }
            } catch (error: Exception) {
                loadedPosts = emptyList()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        posts = emptyList(),
                        errorMessage = error.message ?: "Terjadi kesalahan.",
                    )
                }
            }
        }
    }
    // onEvent + Factory as in 5b
}
```

Tests pass a real lambda (`loadPosts = { posts }` or `{ throw IOException() }`), so every branch
of the state machine stays reachable without a data layer existing.

### 5b. `:core:data` exists — take the repository

**Use the `wire-feature-to-data` skill.** Replacing the seam is not a one-line edit and it is not
this skill's job: the domain model and the UI model have different shapes, so a mapper has to be
written, the `Factory` has to be re-checked, and the tests lose the lambda they were driving. That
skill also carries the case this one gets wrong if you improvise — a repository that exists while
`:core:common` still has no `Result`, so failures still arrive as thrown exceptions.

What does **not** change when the repository arrives: `PostListUiState` keeps holding
`PostUiModel`, the event file, the stateless screen, and every component.

Rules: no Android framework types (`Context`, `View`) in the signature; every state change goes
through `_uiState.update`; suspending work runs in `viewModelScope`; the dispatcher comes from
`:core:common`'s `DispatcherProvider` when the work is not main-safe.

## 6. Stateless screen — previewable, screenshot-testable

```kotlin
@Composable
fun PostListScreen(
    uiState: PostListUiState,
    onEvent: (PostListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {      // modifier applied once, to the root
        when {
            uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage,
                onRetry = { onEvent(PostListEvent.Refresh) },
                modifier = Modifier.align(Alignment.Center),
            )
            uiState.isEmpty -> EmptyState(modifier = Modifier.align(Alignment.Center))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(uiState.posts, key = { it.id }) { post ->
                    PostCard(
                        title = post.title,
                        onClick = { onEvent(PostListEvent.PostClicked(post.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostListScreenPreview() {
    MaterialTheme {                           // or the module's own theme wrapper, if it has one
        PostListScreen(                       // literal state: no ViewModel, no LaunchedEffect
            // PostUiModel, never the domain model — that is what `posts` holds.
            uiState = PostListUiState(posts = listOf(PostUiModel(1, "Sample title", "Ringkasan"))),
            onEvent = {},
        )
    }
}
```

Add a preview per meaningful state (loading, error, empty, content) — they are the cheapest
regression check this screen has.

## 7. Route — the only stateful composable

```kotlin
@Composable
fun PostListRoute(
    modifier: Modifier = Modifier,
    viewModel: PostListViewModel = viewModel(factory = PostListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PostListScreen(uiState = uiState, onEvent = viewModel::onEvent, modifier = modifier)
}
```

`collectAsStateWithLifecycle()` from `lifecycle-runtime-compose` is the Compose equivalent of
`repeatOnLifecycle(STARTED)`. Nothing else belongs in the Route.

When the screen has a `PostListEffect`, the Route is also the only place that collects it — the
callback it invokes is a parameter, so the Route still decides nothing itself:

```kotlin
// Unit as the key on purpose: a real key would re-run on recomposition and collect twice.
LaunchedEffect(Unit) {
    viewModel.effects.collect { effect ->
        when (effect) {
            is PostListEffect.NavigateToDetail -> onNavigateToDetail(effect.postId)
        }
    }
}
```

## 8. Component — the API guidelines apply in full

```kotlin
@Composable
fun PostCard(
    title: String,                                        // required data
    modifier: Modifier = Modifier,                        // first optional
    subtitle: String? = null,                             // null means absent
    contentPadding: Dp = PostCardDefaults.ContentPadding, // public default
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,  // slot, scoped
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            trailing?.invoke(this)
        }
    }
}

object PostCardDefaults {
    val ContentPadding: Dp = 16.dp
}
```

## 9. Activity — declared by `:feature`, launcher filter merged by `:app`

The window theme comes from the manifest; inside `setContent`, wrap in `MaterialTheme` — or in the
module's own theme wrapper when it already has one. Do not create one here.

```kotlin
class PostListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PostListRoute() } }
    }
}
```

`feature/src/main/AndroidManifest.xml` — no `android:exported` here:

```xml
<activity android:name=".postlist.PostListActivity" android:theme="@style/Theme.DemoClaudeAgent" />
```

## 10. ViewModel unit test — runs on `testDebugUnitTest`

With the 5a seam the test supplies the lambda directly — no fake class needed:

```kotlin
class PostListViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()   // Dispatchers.setMain / resetMain

    @Test
    fun `failure surfaces the message and clears loading`() = runTest {
        val viewModel = PostListViewModel(loadPosts = { throw IOException("Tidak ada koneksi") })

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals("Tidak ada koneksi", state.errorMessage)
    }

    @Test
    fun `dismissing the error clears it without reloading`() = runTest {
        var calls = 0
        val viewModel = PostListViewModel(loadPosts = { calls++; throw IOException("boom") })

        viewModel.onEvent(PostListEvent.ErrorDismissed)

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, calls)
    }
}
```

Test the state machine: initial state, success, empty success, each failure, and every event.
Fakes and lambdas over mocks — no mocking library is in the catalog.

## 11. Compose UI test — `androidTest`, needs a device

Import `androidx.compose.ui.test.junit4.v2.createComposeRule` — the old
`androidx.compose.ui.test.junit4.createComposeRule` is deprecated in the BOM this repo pins.
The v2 rule uses `StandardTestDispatcher`, so tasks are queued rather than run immediately.

```kotlin
class PostListScreenTest {

    @get:Rule val composeRule = createComposeRule()   // …junit4.v2.createComposeRule

    @Test
    fun retry_emits_refresh_event() {
        val events = mutableListOf<PostListEvent>()
        composeRule.setContent {
            MaterialTheme {
                PostListScreen(
                    uiState = PostListUiState(errorMessage = "boom"),
                    onEvent = events::add,
                )
            }
        }

        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(listOf(PostListEvent.Refresh), events)
    }
}
```

Driving the stateless screen directly is what keeps this test fast and free of the ViewModel.
`setContent` may be called only once per test — a second call fails, so a second scenario is a
second `@Test`.

## 12. Utility — shared logic that is not a UI component

`feature/src/main/java/com/sample/demo/feature/util/QueryFilter.kt`

Framework-free by rule: no `@Composable`, no `androidx.compose.*`, no `android.*`. That is what
lets it be tested as plain Kotlin, with no device, Compose rule, or ViewModel.

```kotlin
package com.sample.demo.feature.util

/**
 * Filters [this] by a user-typed [query], comparing against the text returned by [selector].
 *
 * A blank query matches everything; matching is case-insensitive and ignores surrounding
 * whitespace.
 *
 * @param query the raw text the user typed.
 * @param selector the text to match each element on.
 */
fun <T> List<T>.filterByQuery(query: String, selector: (T) -> String): List<T> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { selector(it).contains(trimmed, ignoreCase = true) }
}
```

Its test is an ordinary JUnit test — no rule, no dispatcher, no Compose:

```kotlin
class QueryFilterTest {
    @Test
    fun `blank query returns every element`() {
        assertEquals(listOf("a", "b"), listOf("a", "b").filterByQuery("   ") { it })
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(listOf("Kotlin"), listOf("Kotlin", "Java").filterByQuery("kot") { it })
    }
}
```

A function belongs here once a second screen needs it, or when it is already fully general as
above — `filterByQuery` names no screen type. A helper written against one screen's models stays
private to that screen until a second caller appears.
