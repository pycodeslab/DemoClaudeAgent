# Templates

Skeletons for the five pieces. Replace `Post`/`post` with the screen name. These are shapes to
follow, not code to paste unchanged — delete what a given screen does not need.

## 0. Theme — create once, shared by every screen

`feature/src/main/java/com/sample/demo/feature/ui/theme/Theme.kt`

```kotlin
package com.sample.demo.feature.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun DemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // dynamicColor is API 31+; minSdk here is 24.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

## 1. UiState + events

`PostListUiState.kt` — one state object, one sealed event hierarchy.

```kotlin
package com.sample.demo.feature.postlist

import com.sample.demo.core.data.model.Post

data class PostListUiState(
    val isLoading: Boolean = false,
    val posts: List<Post> = emptyList(),
    val query: String = "",
    val errorMessage: String? = null,
) {
    // Derived — never a second source of truth, never a second StateFlow.
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && posts.isEmpty()
}

sealed interface PostListEvent {
    data object Refresh : PostListEvent
    data object ErrorDismissed : PostListEvent
    data class QueryChanged(val query: String) : PostListEvent
    data class PostClicked(val postId: Long) : PostListEvent
}
```

Rules: immutable `data class`; add a field rather than a second stream; derived values are
`val ... get()`, not stored copies.

## 2. ViewModel — the only place with business logic

Pick the variant that matches what actually exists. **2a is the current state of this repo.**

### 2a. `:core:data` does not exist yet — provide nothing

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
    // onEvent + Factory as in 2b
}
```

Tests pass a real lambda (`loadPosts = { posts }` or `{ throw IOException() }`), so every branch
of the state machine stays reachable without a data layer existing.

### 2b. `:core:data` exists — take the repository

```kotlin
package com.sample.demo.feature.postlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sample.demo.core.common.Result
import com.sample.demo.core.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PostListViewModel(
    private val repository: PostRepository = PostRepository(),   // default arg: no DI framework
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostListUiState())
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()

    init { load() }

    fun onEvent(event: PostListEvent) {
        when (event) {
            PostListEvent.Refresh -> load()
            PostListEvent.ErrorDismissed -> _uiState.update { it.copy(errorMessage = null) }
            is PostListEvent.QueryChanged -> _uiState.update { it.copy(query = event.query) }
            is PostListEvent.PostClicked -> { /* navigation intent */ }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // Errors cross boundaries as values — branch on Result, do not try/catch.
            when (val result = repository.getPosts()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, posts = result.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.exception.toMessage())
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PostListViewModel() }
        }
    }
}
```

Rules: no Android framework types (`Context`, `View`) in the signature; every state change goes
through `_uiState.update`; suspending work runs in `viewModelScope`; the dispatcher comes from
`:core:common`'s `DispatcherProvider` when the work is not main-safe.

## 3. Stateless screen — previewable, screenshot-testable

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
    DemoTheme {
        PostListScreen(                       // literal state: no ViewModel, no LaunchedEffect
            uiState = PostListUiState(posts = listOf(Post(1, "Sample title", "body"))),
            onEvent = {},
        )
    }
}
```

Add a preview per meaningful state (loading, error, empty, content) — they are the cheapest
regression check this screen has.

## 4. Route — the only stateful composable

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

## 5. Component — the API guidelines apply in full

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

## 6. Activity — declared by `:feature`, launcher filter merged by `:app`

```kotlin
class PostListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DemoTheme { PostListRoute() } }
    }
}
```

`feature/src/main/AndroidManifest.xml` — no `android:exported` here:

```xml
<activity android:name=".postlist.PostListActivity" android:theme="@style/Theme.DemoClaudeAgent" />
```

## 7. ViewModel unit test — runs on `testDebugUnitTest`

With the 2a seam the test supplies the lambda directly — no fake class needed:

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

## 8. Compose UI test — `androidTest`, needs a device

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
            DemoTheme {
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

## 9. Utility — shared logic that is not a UI component

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
