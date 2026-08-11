package com.sample.demo.feature.postlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sample.demo.feature.ui.theme.DemoTheme

/**
 * Failure state with a retry affordance.
 *
 * @param message what went wrong, already user-facing.
 * @param onRetry invoked when the user asks to try again.
 * @param modifier the [Modifier] to apply to this component.
 * @param retryLabel label of the retry button.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Coba lagi",
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(retryLabel) }
    }
}

/**
 * Shown when a load succeeded but produced nothing to display.
 *
 * @param modifier the [Modifier] to apply to this component.
 * @param message the text explaining the emptiness.
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    message: String = "Belum ada post.",
) {
    Text(
        text = message,
        modifier = modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

@Preview(showBackground = true)
@Composable
private fun ErrorStatePreview() {
    DemoTheme(dynamicColor = false) {
        ErrorState(message = "Tidak ada koneksi internet.", onRetry = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    DemoTheme(dynamicColor = false) { EmptyState() }
}
