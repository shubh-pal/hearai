package com.hearai.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.hearai.app.data.model.AppTheme

private val Purple = Color(0xFF6750A4)
private val PurpleLight = Color(0xFFEADDFF)

private val LightColors = lightColorScheme(primary = Purple, secondaryContainer = PurpleLight)
private val DarkColors = darkColorScheme(primary = Color(0xFFD0BCFF))

@Composable
fun HearAiTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> systemDark
    }

    val context = LocalContext.current
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColorSupported && useDark -> dynamicDarkColorScheme(context)
        dynamicColorSupported && !useDark -> dynamicLightColorScheme(context)
        useDark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = MaterialTheme.typography, content = content)
}
