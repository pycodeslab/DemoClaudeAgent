package com.sample.demo.feature.postlist

import com.sample.demo.feature.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class PostListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val posts = listOf(
        PostUiModel(1, "Kotlin coroutines", "Ringkasan satu."),
        PostUiModel(2, "Compose layouts", "Ringkasan dua."),
        PostUiModel(3, "Kotlin flows", "Ringkasan tiga."),
    )

    @Test
    fun `default provides nothing, so the screen starts empty`() = runTest {
        // No :core:data in this repo — the default seam loads nothing, by design.
        val viewModel = PostListViewModel()

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.posts.isEmpty())
        assertNull(state.errorMessage)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `successful load exposes posts and stops loading`() = runTest {
        val viewModel = PostListViewModel(loadPosts = { posts })

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(posts, state.posts)
        assertNull(state.errorMessage)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `failure surfaces the message and clears loading`() = runTest {
        val viewModel = PostListViewModel(loadPosts = { throw IOException("Tidak ada koneksi") })

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals("Tidak ada koneksi", state.errorMessage)
        assertTrue(state.posts.isEmpty())
        // An error is not emptiness — the screen must show retry, not "no posts".
        assertFalse(state.isEmpty)
    }

    @Test
    fun `query filters the loaded posts case-insensitively`() = runTest {
        val viewModel = PostListViewModel(loadPosts = { posts })

        viewModel.onEvent(PostListEvent.QueryChanged("kotlin"))

        val state = viewModel.uiState.value
        assertEquals("kotlin", state.query)
        assertEquals(listOf(posts[0], posts[2]), state.posts)
    }

    @Test
    fun `blank query restores every post`() = runTest {
        val viewModel = PostListViewModel(loadPosts = { posts })

        viewModel.onEvent(PostListEvent.QueryChanged("compose"))
        viewModel.onEvent(PostListEvent.QueryChanged("   "))

        assertEquals(posts, viewModel.uiState.value.posts)
    }

    @Test
    fun `refresh reloads and keeps the active query applied`() = runTest {
        var calls = 0
        val viewModel = PostListViewModel(loadPosts = { calls++; posts })

        viewModel.onEvent(PostListEvent.QueryChanged("compose"))
        viewModel.onEvent(PostListEvent.Refresh)

        assertEquals(2, calls)
        assertEquals(listOf(posts[1]), viewModel.uiState.value.posts)
    }

    @Test
    fun `dismissing the error clears it without reloading`() = runTest {
        var calls = 0
        val viewModel = PostListViewModel(
            loadPosts = { calls++; throw IOException("Tidak ada koneksi") },
        )

        viewModel.onEvent(PostListEvent.ErrorDismissed)

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, calls)
    }
}
