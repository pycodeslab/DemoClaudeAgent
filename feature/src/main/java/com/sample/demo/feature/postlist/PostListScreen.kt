package com.sample.demo.feature.postlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sample.demo.feature.postlist.components.EmptyState
import com.sample.demo.feature.postlist.components.ErrorState
import com.sample.demo.feature.postlist.components.PostCard
import com.sample.demo.feature.ui.theme.DemoTheme

/**
 * The post list, rendered purely from [uiState].
 *
 * Stateless on purpose: no ViewModel reference, so previews and UI tests can drive it directly.
 *
 * @param uiState everything to render.
 * @param onEvent receives every user intent; the caller decides what they mean.
 * @param modifier the [Modifier] to apply to this component.
 */
@Composable
fun PostListScreen(
    uiState: PostListUiState,
    onEvent: (PostListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { onEvent(PostListEvent.QueryChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Cari post") },
            singleLine = true,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage,
                    onRetry = { onEvent(PostListEvent.Refresh) },
                    modifier = Modifier.align(Alignment.Center),
                )

                uiState.isEmpty -> EmptyState(modifier = Modifier.align(Alignment.Center))

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = uiState.posts, key = { it.id }) { post ->
                        PostCard(
                            title = post.title,
                            modifier = Modifier.fillMaxWidth(),
                            excerpt = post.excerpt,
                            onClick = { onEvent(PostListEvent.PostClicked(post.id)) },
                        )
                    }
                }
            }
        }
    }
}

private val SamplePosts = listOf(
    PostUiModel(1, "Post 1", "Ringkasan singkat untuk post nomor 1."),
    PostUiModel(2, "Post 2", "Ringkasan singkat untuk post nomor 2."),
)

@Preview(showBackground = true, name = "Content")
@Composable
private fun PostListScreenContentPreview() {
    DemoTheme(dynamicColor = false) {
        PostListScreen(uiState = PostListUiState(posts = SamplePosts), onEvent = {})
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun PostListScreenLoadingPreview() {
    DemoTheme(dynamicColor = false) {
        PostListScreen(uiState = PostListUiState(isLoading = true), onEvent = {})
    }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun PostListScreenErrorPreview() {
    DemoTheme(dynamicColor = false) {
        PostListScreen(
            uiState = PostListUiState(errorMessage = "Tidak ada koneksi internet."),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun PostListScreenEmptyPreview() {
    DemoTheme(dynamicColor = false) {
        PostListScreen(uiState = PostListUiState(), onEvent = {})
    }
}
