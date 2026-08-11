package com.sample.demo.feature.postlist

/**
 * Everything [PostListScreen] needs to render, in one object.
 *
 * Add a field here rather than exposing a second stream from the ViewModel.
 */
data class PostListUiState(
    val isLoading: Boolean = false,
    val posts: List<PostUiModel> = emptyList(),
    val query: String = "",
    val errorMessage: String? = null,
) {
    /** Derived, not stored — a second copy of this could disagree with [posts]. */
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && posts.isEmpty()
}

/** A post as the list renders it: pre-formatted, no domain or wire types. */
data class PostUiModel(
    val id: Long,
    val title: String,
    val excerpt: String,
)

/** Every user intent on this screen. One `onEvent` keeps the screen signature stable. */
sealed interface PostListEvent {
    data object Refresh : PostListEvent
    data object ErrorDismissed : PostListEvent
    data class QueryChanged(val query: String) : PostListEvent
    data class PostClicked(val postId: Long) : PostListEvent
}
