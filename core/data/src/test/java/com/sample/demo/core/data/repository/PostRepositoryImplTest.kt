package com.sample.demo.core.data.repository

import com.sample.demo.core.data.model.Post
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests see `internal` declarations of the same module, so the impl and its seam are reachable
 * without widening any visibility.
 */
class PostRepositoryImplTest {

    @Test
    fun `returns what the seam fetched`() = runTest {
        val posts = listOf(Post(1, "Judul", "Isi"))
        val repository = PostRepositoryImpl(fetchPosts = { posts })

        assertEquals(posts, repository.getPosts())
    }

    @Test
    fun `default fetches nothing while core network is empty`() = runTest {
        assertEquals(emptyList<Post>(), PostRepository().getPosts())
    }

    @Test
    fun `a failing seam surfaces its exception until Result exists`() = runTest {
        val repository = PostRepositoryImpl(fetchPosts = { throw IOException("offline") })

        val error = runCatching { repository.getPosts() }.exceptionOrNull()

        assertTrue(error is IOException)
    }
}
