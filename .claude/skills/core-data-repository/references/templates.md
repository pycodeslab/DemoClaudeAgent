# Templates

Skeletons for `:core:data`. Replace `Post`/`post` with the domain name. Shapes to follow, not code
to paste unchanged — delete what a given repository does not need.

Two variants. Pick with the table in `SKILL.md`:

- **A. Canonical** — `:core:common` and `:core:network` are implemented.
- **B. Seam** — one or both are still empty. Fabricate nothing; leave a function-type parameter.

---

## A. Canonical

### A1. Domain model — `model/Post.kt`

Shaped by what the app needs. No nullability the app would have to re-check, no field nobody reads.

```kotlin
package com.sample.demo.core.data.model

/** Domain model. Deliberately not the wire shape — see [com.sample.demo.core.data.mapper]. */
data class Post(
    val id: Int,
    val title: String,
    val body: String,
)
```

### A2. Mapper — `mapper/PostMapper.kt`

`internal` — the signature names `PostDto`. Every null from the wire is resolved here, which is why
`Post` above has none.

```kotlin
package com.sample.demo.core.data.mapper

import com.sample.demo.core.data.model.Post
import com.sample.demo.core.network.model.PostDto

internal fun PostDto.toDomain(): Post = Post(
    id = id ?: 0,
    title = title.orEmpty().trim(),
    body = body.orEmpty().trim(),
)
```

### A3. Remote data source — `datasource/PostRemoteDataSource.kt`

Interface and implementation share the file; both are `internal` because both speak `PostDto`. This
is the only place `PostApi` and `safeApiCall` appear. No mapping happens here.

```kotlin
package com.sample.demo.core.data.datasource

import com.sample.demo.core.common.Result
import com.sample.demo.core.network.api.PostApi
import com.sample.demo.core.network.model.PostDto
import com.sample.demo.core.network.safeApiCall

internal interface PostRemoteDataSource {
    suspend fun getPosts(): Result<List<PostDto>>
}

internal class PostRemoteDataSourceImpl(
    private val api: PostApi,
) : PostRemoteDataSource {

    override suspend fun getPosts(): Result<List<PostDto>> = safeApiCall { api.getPosts() }
}
```

### A4. Repository interface + factory — `repository/PostRepository.kt`

The module's entire public surface. Only `:core:common` types and this module's domain models may
appear in it. The factory is public while every type it builds is `internal` — legal in Kotlin,
because internal types appear in its *body*, never its signature.

```kotlin
package com.sample.demo.core.data.repository

import com.sample.demo.core.common.Result
import com.sample.demo.core.data.datasource.PostRemoteDataSourceImpl
import com.sample.demo.core.data.model.Post
import com.sample.demo.core.network.NetworkModule

interface PostRepository {
    suspend fun getPosts(): Result<List<Post>>
}

/** The one public, network-free way to obtain a [PostRepository]. */
fun PostRepository(): PostRepository =
    PostRepositoryImpl(PostRemoteDataSourceImpl(NetworkModule.createPostApi()))
```

### A5. Repository implementation — `repository/PostRepositoryImpl.kt`

Orchestration only: fetch, map, off the main thread. Branches on `Result`; never catches.

```kotlin
package com.sample.demo.core.data.repository

import com.sample.demo.core.common.DefaultDispatcherProvider
import com.sample.demo.core.common.DispatcherProvider
import com.sample.demo.core.common.Result
import com.sample.demo.core.common.map
import com.sample.demo.core.data.datasource.PostRemoteDataSource
import com.sample.demo.core.data.mapper.toDomain
import com.sample.demo.core.data.model.Post
import kotlinx.coroutines.withContext

internal class PostRepositoryImpl(
    private val remote: PostRemoteDataSource,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : PostRepository {

    override suspend fun getPosts(): Result<List<Post>> = withContext(dispatchers.io) {
        // Failures pass through untouched — classification already happened in safeApiCall.
        remote.getPosts().map { dtos -> dtos.map { it.toDomain() } }
    }
}
```

If `:core:common` exposes `map`/`fold` as members rather than extensions, drop the import. If it
exposes neither, branch explicitly — still no `try`/`catch`:

```kotlin
when (val result = remote.getPosts()) {
    is Result.Success -> Result.Success(result.data.map { it.toDomain() })
    is Result.Failure -> result
}
```

---

## B. Seam — a layer below is missing

What today's repo gets: `:core:common` and `:core:network` hold only `.gitkeep`. There is no
`Result`, no `PostDto`, no `PostApi`, so there is no mapper and no data source to write. Build the
model, the contract and the impl; leave one function-type parameter where the source will go.

`model/Post.kt` is unchanged from A1.

`repository/PostRepository.kt`:

```kotlin
package com.sample.demo.core.data.repository

import com.sample.demo.core.data.model.Post

/**
 * Public contract for post data — the only surface `:feature` may use.
 *
 * Returns `List<Post>` rather than `core.common.Result` because `:core:common` does not exist yet;
 * a failing load therefore throws. When `Result` lands, change this to `Result<List<Post>>` and
 * delete the exception path from callers.
 */
interface PostRepository {
    suspend fun getPosts(): List<Post>
}

/** The one public way to obtain a [PostRepository]. Loads nothing until `:core:network` exists. */
fun PostRepository(): PostRepository = PostRepositoryImpl()
```

`repository/PostRepositoryImpl.kt`:

```kotlin
package com.sample.demo.core.data.repository

import com.sample.demo.core.data.model.Post

/**
 * [fetchPosts] is a seam, not a data source. `:core:network` has no `PostApi` or `PostDto` yet, so
 * nothing is provided and the default fetches nothing. When the network layer lands, replace this
 * parameter with `PostRemoteDataSource`, add `mapper/PostMapper.kt`, and map `PostDto` to [Post]
 * here. Deliberately *not* a fake API, a stub DTO or a hardcoded list — those are `:core:network`'s
 * code in the wrong module.
 */
internal class PostRepositoryImpl(
    private val fetchPosts: suspend () -> List<Post> = { emptyList() },
) : PostRepository {

    override suspend fun getPosts(): List<Post> = fetchPosts()
}
```

Why a function type rather than an interface declared here: an interface plus an implementation *is*
a network layer, just one in the wrong module. A function type adds no types, keeps the repository
fully testable (tests pass a real lambda, including one that throws), and collapses to a single
constructor parameter when the real data source arrives.

Do not add `withContext(dispatchers.io)` in this variant — with nothing to map there is no work to
move, and `DispatcherProvider` belongs to `:core:common`, which does not exist yet.
