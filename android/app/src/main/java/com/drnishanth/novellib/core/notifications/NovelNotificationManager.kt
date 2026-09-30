package com.drnishanth.novellib.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.drnishanth.novellib.ui.MainActivity

object NovelNotificationManager {

    const val CHANNEL_NEW_CHAPTERS = "channel_new_chapters"
    const val CHANNEL_DOWNLOADS = "channel_downloads"
    const val CHANNEL_SOURCES = "channel_sources"

    const val EXTRA_NOVEL_ID = "extra_novel_id"
    const val EXTRA_PROFILE_ID = "extra_profile_id"

    /**
     * Initializes all notification channels required by NovelLib (API 26+).
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val newChaptersChannel = NotificationChannel(
                CHANNEL_NEW_CHAPTERS,
                "New Chapter Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when followed novels release new chapters"
                enableVibration(true)
            }

            val downloadsChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background chapter download progress and completions"
            }

            val sourcesChannel = NotificationChannel(
                CHANNEL_SOURCES,
                "Source Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Declarative source definitions and registry updates"
            }

            notificationManager.createNotificationChannels(
                listOf(newChaptersChannel, downloadsChannel, sourcesChannel)
            )
        }
    }

    /**
     * Checks if the app currently has permission to post notifications (API 33+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Dispatches a notification for newly discovered chapters of a followed novel.
     */
    fun showNewChaptersNotification(
        context: Context,
        profileId: String,
        novelId: String,
        novelTitle: String,
        newChaptersCount: Int,
        firstChapterTitle: String? = null
    ) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOVEL_ID, novelId)
            putExtra(EXTRA_PROFILE_ID, profileId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            novelId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (newChaptersCount == 1) {
            "New Chapter: $novelTitle"
        } else {
            "$newChaptersCount New Chapters: $novelTitle"
        }

        val content = if (!firstChapterTitle.isNullOrBlank()) {
            if (newChaptersCount > 1) "$firstChapterTitle and ${newChaptersCount - 1} more" else firstChapterTitle
        } else {
            "New chapter content ready to read"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_CHAPTERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(novelId.hashCode(), notification)
    }

    /**
     * Dispatches a notification when queued chapter downloads finish.
     */
    fun showDownloadCompletedNotification(
        context: Context,
        completedCount: Int,
        novelTitle: String? = null
    ) {
        if (!hasNotificationPermission(context)) return

        val title = novelTitle?.let { "$it: Download Complete" } ?: "Downloads Complete"
        val content = "$completedCount chapter(s) downloaded and ready for offline reading."

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(2001, notification)
    }

    /**
     * Dispatches a notification when chapter downloads encounter errors.
     */
    fun showDownloadFailedNotification(
        context: Context,
        failedCount: Int,
        novelTitle: String? = null
    ) {
        if (!hasNotificationPermission(context)) return

        val title = novelTitle?.let { "$it: Download Failed" } ?: "Download Issues"
        val content = "Failed to download $failedCount chapter(s). Please check your internet connection."

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(2002, notification)
    }

    /**
     * Dispatches a notification when declarative source definitions are updated.
     */
    fun showSourceUpdateNotification(
        context: Context,
        updatedCount: Int
    ) {
        if (!hasNotificationPermission(context)) return

        val title = "Novel Sources Updated"
        val content = "$updatedCount source definition(s) updated successfully."

        val notification = NotificationCompat.Builder(context, CHANNEL_SOURCES)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(3001, notification)
    }
}
