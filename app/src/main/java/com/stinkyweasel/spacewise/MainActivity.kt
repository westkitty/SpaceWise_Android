package com.stinkyweasel.spacewise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.stinkyweasel.spacewise.ui.navigation.SpaceWiseNavGraph
import com.stinkyweasel.spacewise.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var darkThemeState by remember { mutableStateOf<Boolean?>(null) }
            val isDark = darkThemeState ?: isSystemInDarkTheme()

            MyApplicationTheme(darkTheme = isDark) {
                SpaceWiseNavGraph(
                    darkTheme = isDark,
                    onToggleTheme = { darkThemeState = !isDark }
                )
            }
        }
    }
}
