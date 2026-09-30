package com.drnishanth.novellib.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.drnishanth.novellib.ui.navigation.NovelNavGraph
import com.drnishanth.novellib.ui.theme.NovelLibTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NovelLibTheme {
                val navController = rememberNavController()
                NovelNavGraph(navController = navController)
            }
        }
    }
}
