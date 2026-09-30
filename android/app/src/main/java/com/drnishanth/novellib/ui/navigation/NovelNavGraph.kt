package com.drnishanth.novellib.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.drnishanth.novellib.NovelLibApplication
import com.drnishanth.novellib.ui.library.LibraryScreen
import com.drnishanth.novellib.ui.library.LibraryViewModel
import com.drnishanth.novellib.ui.novel.NovelDetailScreen
import com.drnishanth.novellib.ui.novel.NovelDetailViewModel
import com.drnishanth.novellib.ui.profiles.ProfilePickerScreen
import com.drnishanth.novellib.ui.profiles.ProfileViewModel
import com.drnishanth.novellib.ui.reader.ReaderScreen
import com.drnishanth.novellib.ui.reader.ReaderViewModel
import com.drnishanth.novellib.ui.sources.SourcesScreen
import com.drnishanth.novellib.ui.sources.SourcesViewModel

@Composable
fun NovelNavGraph(
    navController: NavHostController,
    initialNovelId: String? = null
) {
    val profileRepository = NovelLibApplication.instance.profileRepository
    val activeProfile by profileRepository.activeProfile.collectAsState()

    val startDestination = if (activeProfile != null) Screen.Library.route else Screen.Profiles.route

    LaunchedEffect(initialNovelId, activeProfile) {
        if (activeProfile != null && !initialNovelId.isNullOrBlank()) {
            navController.navigate(Screen.NovelDetail.createRoute(initialNovelId))
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Profiles.route) {
            val profileVm: ProfileViewModel = viewModel()
            ProfilePickerScreen(
                viewModel = profileVm,
                onProfileSelected = {
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Profiles.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Library.route) {
            val libraryVm: LibraryViewModel = viewModel()
            LibraryScreen(
                viewModel = libraryVm,
                onNovelSelected = { novelId ->
                    navController.navigate(Screen.NovelDetail.createRoute(novelId))
                },
                onSwitchProfile = {
                    navController.navigate(Screen.Profiles.route) {
                        popUpTo(Screen.Library.route) { inclusive = true }
                    }
                },
                onOpenSources = {
                    navController.navigate(Screen.Sources.route)
                },
                onOpenSync = {
                    navController.navigate(Screen.Sync.route)
                }
            )
        }

        composable(Screen.Sources.route) {
            val sourcesVm: SourcesViewModel = viewModel()
            SourcesScreen(
                viewModel = sourcesVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Sync.route) {
            val syncVm: com.drnishanth.novellib.ui.sync.SyncViewModel = viewModel()
            com.drnishanth.novellib.ui.sync.SyncScreen(
                viewModel = syncVm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.NovelDetail.route,
            arguments = listOf(navArgument("novelId") { type = NavType.StringType })
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
            val novelDetailVm = viewModel<NovelDetailViewModel>(
                key = novelId,
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return NovelDetailViewModel(novelId) as T
                    }
                }
            )
            NovelDetailScreen(
                viewModel = novelDetailVm,
                onBack = { navController.popBackStack() },
                onOpenChapter = { chapterId ->
                    navController.navigate(Screen.Reader.createRoute(novelId, chapterId))
                }
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("novelId") { type = NavType.StringType },
                navArgument("chapterId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""

            val readerVm = viewModel<ReaderViewModel>(
                key = "$novelId-$chapterId",
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return ReaderViewModel(novelId, chapterId) as T
                    }
                }
            )
            ReaderScreen(
                viewModel = readerVm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
