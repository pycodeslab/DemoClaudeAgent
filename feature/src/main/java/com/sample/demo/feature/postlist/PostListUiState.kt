package com.sample.demo.feature.postlist

import com.sample.demo.core.data.model.Post

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

/**
 * The one place [Post]'s shape and this screen's shape are reconciled.
 *
 * Lives here rather than in `util/` (it names one screen's UI model) or in `:core:data` (a UI model
 * there would invert the dependency).
 */
internal fun Post.toUiModel(): PostUiModel = PostUiModel(
    id = id.toLong(),                      // the domain says Int; the list wants a stable Long key
    title = title,
    excerpt = body.take(EXCERPT_LENGTH),   // the list shows a preview, not the whole body
)

private const val EXCERPT_LENGTH = 120

/** Every user intent on this screen. One `onEvent` keeps the screen signature stable. */
sealed interface PostListEvent {
    data object Refresh : PostListEvent
    data object ErrorDismissed : PostListEvent
    data class QueryChanged(val query: String) : PostListEvent
    data class PostClicked(val postId: Long) : PostListEvent
}
