package com.sample.demo.feature.postlist.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sample.demo.feature.ui.theme.DemoTheme

/**
 * A single post in a list.
 *
 * @param title the post title; the primary line and the accessibility label of the card.
 * @param modifier the [Modifier] to apply to this component.
 * @param excerpt supporting line under the title, or `null` when the post has none.
 * @param contentPadding padding between the card edge and its content.
 * @param onClick invoked when the card is tapped, or `null` for a non-interactive card.
 * @param trailing optional slot at the end of the row — an icon, a badge, whatever the caller needs.
 */
@Composable
fun PostCard(
    title: String,
    modifier: Modifier = Modifier,
    excerpt: String? = null,
    contentPadding: Dp = PostCardDefaults.ContentPadding,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (excerpt != null) {
                    Text(
                        text = excerpt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}

/** Public defaults, so callers wrapping [PostCard] can reproduce them. */
object PostCardDefaults {
    val ContentPadding: Dp = 16.dp
}

@Preview(showBackground = true)
@Composable
private fun PostCardPreview() {
    DemoTheme(dynamicColor = false) {
        PostCard(
            title = "Post 1",
            excerpt = "Ringkasan singkat untuk post nomor 1.",
            onClick = {},
        )
    }
}
