package com.homehabit.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

        // Exigence "toujours allumé" : usage type ecran mural.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Rallumage par-dessus l'ecran verrouille (utilise par
        // ScreenPowerController.performScreenOn apres une extinction
        // reelle planifiee). Les attributs XML showWhenLocked/turnScreenOn
        // du manifest ne s'appliquent qu'a partir de l'API 27 : ces flags
        // fenetre couvrent aussi les versions plus anciennes (minSdk 23),
        // au prix d'une API depreciee mais toujours fonctionnelle.
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
}
