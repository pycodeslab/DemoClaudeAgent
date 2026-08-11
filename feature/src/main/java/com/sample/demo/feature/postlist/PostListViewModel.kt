package com.sample.demo.feature.postlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sample.demo.feature.util.filterByQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * All business logic for the post list: load lifecycle, failure handling, and search filtering.
 *
 * No DI framework (see CLAUDE.md) — the dependency is a constructor parameter with a default.
 *
 * [loadPosts] is a seam, not a data layer. `:core:data` does not exist in this repo yet, so
 * nothing is provided: the default loads nothing and the screen renders its empty state. When
 * `PostRepository` lands, change the default to call it and replace the `try/catch` with a
 * `when (result)` over `core.common.Result`. Deliberately *not* a stub repository or fake data
 * source — that would be a data layer in the wrong module.
 */
class PostListViewModel(
    private val loadPosts: suspend () -> List<PostUiModel> = { emptyList() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostListUiState())
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()

    /** Unfiltered result of the last load; [PostListUiState.posts] is the filtered view of it. */
    private var loadedPosts: List<PostUiModel> = emptyList()

    init {
        load()
    }

    fun onEvent(event: PostListEvent) {
        when (event) {
            PostListEvent.Refresh -> load()

            PostListEvent.ErrorDismissed -> _uiState.update { it.copy(errorMessage = null) }

            is PostListEvent.QueryChanged -> _uiState.update { state ->
                state.copy(
                    query = event.query,
                    posts = loadedPosts.filterByQuery(event.query) { it.title },
                )
            }

            is PostListEvent.PostClicked -> Unit // navigation belongs to the caller of the Route
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // try/catch only because :core:common has no Result type yet; once it does, errors
            // arrive as values and this becomes a `when` over Result.
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PostListViewModel() }
        }
    }
}
