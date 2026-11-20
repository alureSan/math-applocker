package com.mathapplocker.foss.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathapplocker.foss.data.Prefs
import com.mathapplocker.foss.ui.screens.AppsScreen
import com.mathapplocker.foss.ui.screens.HomeScreen
import com.mathapplocker.foss.ui.screens.MathSettingsScreen
import com.mathapplocker.foss.ui.screens.PinAuthScreen
import com.mathapplocker.foss.ui.screens.PinSetupScreen
import com.mathapplocker.foss.ui.screens.QuestionsScreen

enum class Screen { Home, Math, Apps, Questions }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val settings by prefs.settings.collectAsStateWithLifecycle()

    var screenName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    val screen = Screen.entries.firstOrNull { it.name == screenName } ?: Screen.Home
    val navigate: (Screen) -> Unit = { screenName = it.name }

    when {
        // First run: a PIN is mandatory so settings cannot be changed by the child.
        !settings.hasPin -> PinSetupScreen(onDone = { /* Prefs update drives recomposition. */ })

        !settings.authed -> PinAuthScreen(onSuccess = { /* Prefs update drives recomposition. */ })

        screen == Screen.Home -> HomeScreen(
            settings = settings,
            onNavigate = navigate,
        )

        screen == Screen.Math -> MathSettingsScreen(
            settings = settings,
            prefs = prefs,
            onBack = { navigate(Screen.Home) },
        )

        screen == Screen.Apps -> AppsScreen(
            settings = settings,
            prefs = prefs,
            onBack = { navigate(Screen.Home) },
        )

        screen == Screen.Questions -> QuestionsScreen(
            prefs = prefs,
            onBack = { navigate(Screen.Home) },
        )
    }
}
