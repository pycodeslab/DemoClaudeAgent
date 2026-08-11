package com.sample.demo.feature.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 theme for every Compose screen in `:feature`.
 *
 * The XML `Theme.DemoClaudeAgent` in `:app` still styles the window (status bar, splash);
 * this supplies the color scheme Compose reads through [MaterialTheme].
 *
 * @param darkTheme whether to use the dark color scheme; follows the system by default.
 * @param dynamicColor whether to derive colors from the wallpaper. Honoured on API 31+ only —
 *   `minSdk` here is 24, so lower versions fall back to the static schemes.
 */
@Composable
fun DemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
