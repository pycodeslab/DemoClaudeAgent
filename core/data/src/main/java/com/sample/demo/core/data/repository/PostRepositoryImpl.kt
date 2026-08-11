package com.sample.demo.core.data.repository

import com.sample.demo.core.data.model.Post

/**
 * [fetchPosts] is a seam, not a data source.
 *
 * `:core:network` has no `PostApi` or `PostDto` in this repo yet, so nothing is provided and the
 * default fetches nothing. When the network layer lands, replace this parameter with an internal
 * `PostRemoteDataSource`, add `mapper/PostMapper.kt`, and map `PostDto` to [Post] here — behind
 * `withContext(dispatchers.io)` once `:core:common` supplies a `DispatcherProvider`.
 *
 * Deliberately *not* a fake API, a stub DTO or a hardcoded list: those are `:core:network`'s code
 * living in the wrong module (see CLAUDE.md, "Never fabricate a missing layer").
 *
 * The constructor is `internal` by class visibility; build one through [PostRepository].
 */
internal class PostRepositoryImpl(
    private val fetchPosts: suspend () -> List<Post> = { emptyList() },
) : PostRepository {

    override suspend fun getPosts(): List<Post> = fetchPosts()
}
