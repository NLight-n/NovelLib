package com.drnishanth.novellib.downloads.models

enum class DownloadState(val value: String) {
    NOT_DOWNLOADED("not_downloaded"),
    QUEUED("queued"),
    DOWNLOADING("downloading"),
    AVAILABLE("available"),
    FAILED("failed"),
    ACTION_REQUIRED("action_required");

    companion object {
        fun fromString(str: String): DownloadState =
            entries.firstOrNull { it.value.equals(str, ignoreCase = true) } ?: NOT_DOWNLOADED
    }
}

enum class RetentionPolicy(val value: String) {
    CACHE("cache"),
    OFFLINE("offline");

    companion object {
        fun fromString(str: String): RetentionPolicy =
            entries.firstOrNull { it.value.equals(str, ignoreCase = true) } ?: CACHE
    }
}

enum class StoragePolicy(val value: String) {
    ONLINE("online"),
    OFFLINE("offline"),
    HYBRID("hybrid");

    companion object {
        fun fromString(str: String): StoragePolicy =
            entries.firstOrNull { it.value.equals(str, ignoreCase = true) } ?: HYBRID
    }
}

enum class HybridPolicyOption(val value: String, val label: String) {
    LATEST_N_CHAPTERS("latest_n_chapters", "Latest N Chapters"),
    LATEST_N_UNREAD_CHAPTERS("latest_n_unread_chapters", "Next N Unread Chapters"),
    ALL_UNREAD_CHAPTERS("all_unread_chapters", "All Unread Chapters"),
    ALL_CHAPTERS("all_chapters", "All Chapters"),
    MANUAL_SELECTION("manual_selection", "Manual Selection");

    companion object {
        fun fromString(str: String): HybridPolicyOption =
            entries.firstOrNull { it.value.equals(str, ignoreCase = true) } ?: LATEST_N_UNREAD_CHAPTERS
    }
}

data class ChapterDownloadStatus(
    val chapterId: String,
    val state: DownloadState,
    val progress: Float = 0f,
    val errorMessage: String? = null
)
