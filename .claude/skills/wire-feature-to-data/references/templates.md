# Wiring templates

Two variants. Pick with the table in `SKILL.md` — §A is this repo today, §B is where it goes once
`:core:common` lands.

## A. Repository exists, `Result` does not

### A1. Mapper + UI model

```kotlin
package com.sample.demo.feature.postlist

import com.sample.demo.core.data.model.Post

data class PostListUiState(
    val isLoading: Boolean = false,
    val posts: List<PostUiModel> = emptyList(),   // never List<Post>
    val query: String = "",
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && posts.isEmpty()
}

data class PostUiModel(
    val id: Long,
    val title: String,
    val excerpt: String,
)

/** The one place the domain shape and the UI shape are reconciled. */
internal fun Post.toUiModel(): PostUiModel = PostUiModel(
    id = id.toLong(),                      // domain says Int, the list wants a stable Long key
    title = title,
    excerpt = body.take(EXCERPT_LENGTH),   // the screen shows a preview, not the whole body
)

private const val EXCERPT_LENGTH = 120
```

### A2. ViewModel

```kotlin
package com.sample.demo.feature.postlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sample.demo.core.data.repository.PostRepository
import com.sample.demo.feature.util.filterByQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PostListViewModel(
    // No DI framework: the interface, defaulted to :core:data's public factory function.
    private val repository: PostRepository = PostRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostListUiState())
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()

    private var loadedPosts: List<PostUiModel> = emptyList()

    init { load() }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // try/catch only because :core:common has no Result yet — the repository throws
            // instead of returning a failure value. This becomes `when (result)` later.
            try {
                loadedPosts = repository.getPosts().map { it.toUiModel() }
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

    companion object {
        // The default argument is the real repository, so this needs no change — confirm it.
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PostListViewModel() }
        }
    }
}
```

### A3. Fake + tests

```kotlin
private class FakePostRepository(
    private val posts: List<Post> = emptyList(),
    private val error: Throwable? = null,
) : PostRepository {
    var loadCount = 0
        private set

    override suspend fun getPosts(): List<Post> {
        loadCount++
        error?.let { throw it }
        return posts
    }
}

@Test
fun `domain posts are mapped to UI models`() = runTest {
    val viewModel = PostListViewModel(FakePostRepository(listOf(Post(7, "Judul", "a".repeat(200)))))

    val post = viewModel.uiState.value.posts.single()

    assertEquals(7L, post.id)          // Int -> Long
    assertEquals("Judul", post.title)
    assertEquals(120, post.excerpt.length)   // body truncated
}

@Test
fun `failure surfaces the message and clears loading`() = runTest {
    val viewModel = PostListViewModel(FakePostRepository(error = IOException("Tidak ada koneksi")))

    val state = viewModel.uiState.value

    assertEquals("Tidak ada koneksi", state.errorMessage)
    assertTrue(state.posts.isEmpty())
    assertFalse(state.isEmpty)   // an error is not emptiness — the screen shows retry
}

@Test
fun `refresh reloads and keeps the active query applied`() = runTest {
    val repository = FakePostRepository(posts)
    val viewModel = PostListViewModel(repository)

    viewModel.onEvent(PostListEvent.QueryChanged("compose"))
    viewModel.onEvent(PostListEvent.Refresh)

    assertEquals(2, repository.loadCount)
}
```

`:feature`'s test source sees `:core:data`'s public API through the module's own
`implementation(project(":core:data"))`, so the fake compiles with no build change.

## B. Canonical — `Result` exists

Only `load()` and the failure tests differ from §A.

```kotlin
import com.sample.demo.core.common.Result

private fun load() {
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    viewModelScope.launch {
        // Errors cross boundaries as values — branch, do not catch.
        when (val result = repository.getPosts()) {
            is Result.Success -> {
                loadedPosts = result.data.map { it.toUiModel() }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        posts = loadedPosts.filterByQuery(state.query) { it.title },
                    )
                }
            }

            is Result.Error -> {
                loadedPosts = emptyList()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        posts = emptyList(),
                        errorMessage = result.exception.toMessage(),
                    )
                }
            }
        }
    }
}
```

The fake returns `Result.Success(posts)` / `Result.Error(...)` instead of throwing. `toUiModel`,
`Factory`, `UiState` and the screen are untouched.

## Verify

```powershell
.\gradlew.bat :feature:testDebugUnitTest --tests "*PostListViewModelTest"
.\gradlew.bat :app:assembleDebug
```

Then read for the leaks Gradle cannot see: `grep -r "core.network" feature/src/` empty, and
`UiState` still holding `List<PostUiModel>`.
