package com.drnishanth.novellib.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.drnishanth.novellib.core.notifications.NovelNotificationManager
import com.drnishanth.novellib.ui.navigation.NovelNavGraph
import com.drnishanth.novellib.ui.theme.NovelLibTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val initialNovelId = intent?.getStringExtra(NovelNotificationManager.EXTRA_NOVEL_ID)

        setContent {
            NovelLibTheme {
                val navController = rememberNavController()
                NovelNavGraph(
                    navController = navController,
                    initialNovelId = initialNovelId
                )
            }
        }
    }
}
