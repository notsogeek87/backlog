package com.davidgcd.backlog

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.davidgcd.backlog.notifications.NotificationPreferences
import com.davidgcd.backlog.ui.nav.BacklogNavHost
import com.davidgcd.backlog.ui.theme.BacklogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BacklogApplication
        val repository = app.repository
        val notificationPreferences = NotificationPreferences(applicationContext)

        setContent {
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* release reminders simply won't post until granted */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            BacklogTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BacklogNavHost(
                        repository = repository,
                        notificationPreferences = notificationPreferences,
                        steamService = app.steamService,
                        metacriticService = app.metacriticService,
                    )
                }
            }
        }
    }
}
