package com.hearai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hearai.app.ui.nav.HearAiNavHost
import com.hearai.app.ui.theme.HearAiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HearAiApp() }
    }
}

@Composable
private fun HearAiApp() {
    val themeViewModel: com.hearai.app.ui.AppThemeViewModel = hiltViewModel()
    val appTheme by themeViewModel.theme.collectAsState()

    HearAiTheme(appTheme = appTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HearAiNavHost()
        }
    }
}
