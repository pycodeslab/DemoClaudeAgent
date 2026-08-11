package com.sample.demo.feature.postlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful entry point for the post list.
 *
 * The only place that knows about the ViewModel. [collectAsStateWithLifecycle] is the Compose
 * equivalent of `repeatOnLifecycle(STARTED)`.
 *
 * @param modifier the [Modifier] to apply to this component.
 * @param viewModel the screen's ViewModel; overridable so tests and previews can supply their own.
 */
@Composable
fun PostListRoute(
    modifier: Modifier = Modifier,
    viewModel: PostListViewModel = viewModel(factory = PostListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PostListScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}
