package com.sample.demo.feature.postlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.sample.demo.feature.ui.theme.DemoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Drives the stateless screen directly — no ViewModel, no data source.
 * Needs a device: `.\gradlew.bat :feature:connectedDebugAndroidTest`.
 */
class PostListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun retry_emits_refresh_event() {
        val events = mutableListOf<PostListEvent>()

        composeRule.setContent {
            DemoTheme(dynamicColor = false) {
                PostListScreen(
                    uiState = PostListUiState(errorMessage = "Tidak ada koneksi"),
                    onEvent = events::add,
                )
            }
        }

        composeRule.onNodeWithText("Coba lagi").performClick()

        assertEquals(listOf(PostListEvent.Refresh), events)
    }

    @Test
    fun typing_in_search_emits_query_changed() {
        val events = mutableListOf<PostListEvent>()

        composeRule.setContent {
            DemoTheme(dynamicColor = false) {
                PostListScreen(uiState = PostListUiState(), onEvent = events::add)
            }
        }

        composeRule.onNodeWithText("Cari post").performTextInput("a")

        assertEquals(listOf(PostListEvent.QueryChanged("a")), events)
    }

    @Test
    fun posts_are_rendered() {
        composeRule.setContent {
            DemoTheme(dynamicColor = false) {
                PostListScreen(
                    uiState = PostListUiState(
                        posts = listOf(PostUiModel(1, "Post 1", "Ringkasan")),
                    ),
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("Post 1").assertIsDisplayed()
    }
}
