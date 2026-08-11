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

/**
 * All business logic for the post list: load lifecycle, failure handling, and search filtering.
 *
 * No DI framework (see CLAUDE.md) — the dependency is a constructor parameter with a default.
 *
 * [repository] is `:core:data`'s public contract, defaulted to its public factory function. The
 * screen still renders empty, because `PostRepositoryImpl`'s own seam has no `:core:network` API to
 * call yet — that gap belongs one module down, not here.
 */
class PostListViewModel(
    private val repository: PostRepository = PostRepository(),
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
        // The default argument is the real repository, so the app gets it through this initializer.
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PostListViewModel() }
        }
    }
}
