package com.sample.demo.core.data.repository

import com.sample.demo.core.data.model.Post

/**
 * Public contract for post data — the only surface `:feature` may use.
 *
 * Returns `List<Post>` rather than `core.common.Result` because `:core:common` does not exist in
 * this repo yet, so a failing load throws instead of arriving as a value. When `Result` and
 * `NetworkException` land, this becomes `Result<List<Post>>` and callers branch instead of
 * catching.
 */
interface PostRepository {
    suspend fun getPosts(): List<Post>
}

/**
 * The one public, network-free way to obtain a [PostRepository].
 *
 * Returns a repository that loads nothing: `:core:network` has no `PostApi` yet, so there is no
 * data source to hand it. See [PostRepositoryImpl].
 */
fun PostRepository(): PostRepository = PostRepositoryImpl()
