package com.drnishanth.novellib.ui.navigation

sealed class Screen(val route: String) {
    data object Profiles : Screen("profiles")
    data object Library : Screen("library")
    data object NovelDetail : Screen("novel/{novelId}") {
        fun createRoute(novelId: String) = "novel/$novelId"
    }
    data object Reader : Screen("reader/{novelId}/{chapterId}") {
        fun createRoute(novelId: String, chapterId: String) = "reader/$novelId/$chapterId"
    }
    data object Sources : Screen("sources")
    data object Sync : Screen("sync")
    data object SourceBrowser : Screen("browser?url={url}") {
        fun createRoute(url: String = "https://www.royalroad.com"): String {
            val encoded = java.net.URLEncoder.encode(url, "UTF-8")
            return "browser?url=$encoded"
        }
    }
}
