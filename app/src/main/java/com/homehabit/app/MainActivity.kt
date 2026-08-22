package com.homehabit.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.homehabit.app.ui.dashboard.DashboardScreen
import com.homehabit.app.ui.dashboard.DashboardViewModel
import com.homehabit.app.ui.dashboard.NightModeEffect
import com.homehabit.app.ui.theme.HomeHabitTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val repository = (application as HomeHabitApp).configRepository
                return DashboardViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen mode (Immersive Mode)
        hideSystemUI()

        // "Always on" requirement: wall screen type usage.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Turning back on over the locked screen (used by
        // ScreenPowerController.performScreenOn after a planned
        // actual shutdown). The XML attributes showWhenLocked/turnScreenOn
        // from the manifest only apply from API 27 onwards: these window
        // flags also cover older versions (minSdk 23),
        // at the cost of a deprecated but still functional API.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        viewModel.load()

        setContent {
            HomeHabitTheme {
                val config by viewModel.config.collectAsState()
                NightModeEffect(settings = config.settings)
                DashboardScreen(viewModel = viewModel)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
