package com.sample.demo.feature.postlist

import com.sample.demo.core.data.model.Post
import com.sample.demo.core.data.repository.PostRepository
import com.sample.demo.feature.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Hand-written, because there is no mocking library in this repo and none may be added.
 * [loadCount] exists for the refresh test — reloading is behaviour worth asserting.
 */
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

class PostListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val domainPosts = listOf(
        Post(1, "Kotlin coroutines", "Ringkasan satu."),
        Post(2, "Compose layouts", "Ringkasan dua."),
        Post(3, "Kotlin flows", "Ringkasan tiga."),
    )

    private val posts = domainPosts.map { it.toUiModel() }

    @Test
    fun `default repository provides nothing, so the screen starts empty`() = runTest {
        // :core:network has no API yet, so PostRepository() loads nothing — by design.
        val viewModel = PostListViewModel()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.posts.isEmpty())
        assertNull(state.errorMessage)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `successful load exposes posts and stops loading`() = runTest {
        val viewModel = PostListViewModel(FakePostRepository(domainPosts))

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(posts, state.posts)
        assertNull(state.errorMessage)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `domain posts are mapped to UI models`() = runTest {
        val body = "a".repeat(500)
        val viewModel = PostListViewModel(FakePostRepository(listOf(Post(7, "Judul", body))))

        val post = viewModel.uiState.value.posts.single()

        assertEquals(7L, post.id)                  // Int -> Long
        assertEquals("Judul", post.title)
        assertEquals(body.take(120), post.excerpt) // body truncated to an excerpt
    }

    @Test
    fun `failure surfaces the message and clears loading`() = runTest {
        val viewModel = PostListViewModel(FakePostRepository(error = IOException("Tidak ada koneksi")))

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals("Tidak ada koneksi", state.errorMessage)
        assertTrue(state.posts.isEmpty())
        // An error is not emptiness — the screen must show retry, not "no posts".
        assertFalse(state.isEmpty)
    }

    @Test
    fun `query filters the loaded posts case-insensitively`() = runTest {
        val viewModel = PostListViewModel(FakePostRepository(domainPosts))

        viewModel.onEvent(PostListEvent.QueryChanged("kotlin"))

        val state = viewModel.uiState.value
        assertEquals("kotlin", state.query)
        assertEquals(listOf(posts[0], posts[2]), state.posts)
    }

    @Test
    fun `blank query restores every post`() = runTest {
        val viewModel = PostListViewModel(FakePostRepository(domainPosts))

        viewModel.onEvent(PostListEvent.QueryChanged("compose"))
        viewModel.onEvent(PostListEvent.QueryChanged("   "))

        assertEquals(posts, viewModel.uiState.value.posts)
    }

    @Test
    fun `refresh reloads and keeps the active query applied`() = runTest {
        val repository = FakePostRepository(domainPosts)
        val viewModel = PostListViewModel(repository)

        viewModel.onEvent(PostListEvent.QueryChanged("compose"))
        viewModel.onEvent(PostListEvent.Refresh)

        assertEquals(2, repository.loadCount)
        assertEquals(listOf(posts[1]), viewModel.uiState.value.posts)
    }

    @Test
    fun `dismissing the error clears it without reloading`() = runTest {
        val repository = FakePostRepository(error = IOException("Tidak ada koneksi"))
        val viewModel = PostListViewModel(repository)

        viewModel.onEvent(PostListEvent.ErrorDismissed)

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, repository.loadCount)
    }
}
