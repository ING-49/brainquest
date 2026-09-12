package com.brainquest.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brainquest.game.ui.AppRoot
import com.brainquest.game.ui.theme.AppThemes
import com.brainquest.game.ui.theme.themeScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrainQuestTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(viewModel())
                }
            }
        }
    }
}

@Composable
private fun BrainQuestTheme(content: @Composable () -> Unit) {
    val vm: AppViewModel = viewModel()
    val player by vm.player.collectAsState()
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = themeScheme(AppThemes.byId(player.activeTheme), dark),
        content = content,
    )
}
