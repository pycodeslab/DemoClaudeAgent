package com.sample.demo.feature.postlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sample.demo.feature.ui.theme.DemoTheme

/**
 * Host for [PostListRoute].
 *
 * Declared by `:feature` without `android:exported`; `:app` merges in the MAIN/LAUNCHER filter.
 */
class PostListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DemoTheme {
                PostListRoute()
            }
        }
    }
}
