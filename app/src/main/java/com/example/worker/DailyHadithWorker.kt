package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.HadithsData
import com.example.model.Hadith
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Posts the daily-hadith reminder.
 *
 * The hadith is chosen by [HadithsData.hadithForDay], the same seeded,
 * day-deterministic selection the Home screen's *Daily Insight* card uses, so
 * the notification and the app always agree.
 */
class DailyHadithWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (HadithsData.hadiths.isNotEmpty()) {
                sendNotification(applicationContext, HadithsData.hadithForDay())
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error showing daily hadith notification", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "DailyHadithWorker"
        const val CHANNEL_ID = "daily_hadith_channel"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "daily_hadith_reminder_work"
        const val EXTRA_HADITH_ID = "extra_hadith_id"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Daily Hadith Inspiration"
                val descriptionText = "Daily reminders and inspiring Hadiths"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableLights(true)
                    enableVibration(true)
                }
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            }
        }

        /**
         * Directly creates and posts the notification immediately if permission is granted.
         */
        fun sendNotification(context: Context, hadith: Hadith) {
            createChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                // NEW_TASK + CLEAR_TOP reach the existing task; SINGLE_TOP (with
                // the matching launchMode in the manifest) delivers to the live
                // instance via onNewIntent instead of rebuilding the whole task,
                // which preserves the user's navigation state.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_HADITH_ID, hadith.id)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                hadith.id,
                intent,
                flags
            )

            val title = "Daily Hadith • ${hadith.title}"
            val contentText = hadith.description.ifEmpty { hadith.chapterName }
            val bigText = "\"${hadith.translation}\"\n\n— ${hadith.reference}"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_hadith)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(bigText)
                        .setSummaryText(hadith.chapterName)
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            try {
                val notificationManager = NotificationManagerCompat.from(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    } else {
                        android.util.Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                    }
                } else {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
            } catch (e: SecurityException) {
                android.util.Log.w(TAG, "Permission denied for notifications", e)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to post notification", e)
            }
        }

        /**
         * Schedules a 24-hour periodic work request to trigger daily around 8:00 AM.
         *
         * Uses [ExistingPeriodicWorkPolicy.KEEP] on purpose: the caller computes a
         * fresh `initialDelay` to the next 08:00 on every invocation, so an UPDATE
         * policy would reset the countdown each time the app was opened and could
         * push the reminder out indefinitely for engaged users. KEEP leaves an
         * already-armed period untouched and only enqueues when nothing exists.
         */
        fun scheduleDailyNotification(context: Context, targetHour: Int = 8, targetMinute: Int = 0) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val initialDelay = target.timeInMillis - now.timeInMillis

            val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyHadithWorker>(
                24, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // 15-minute flex interval
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            try {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    dailyWorkRequest
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to enqueue periodic work: ${e.message}", e)
            }
        }

        /**
         * Cancels the daily scheduled notification WorkManager task.
         */
        fun cancelDailyNotification(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to cancel periodic work: ${e.message}", e)
            }
        }

        /**
         * Triggers an immediate notification for the "Send Test Notification"
         * button. Shows today's hadith so the preview matches what the scheduled
         * reminder will say.
         */
        fun triggerImmediateNotification(context: Context) {
            try {
                if (HadithsData.hadiths.isNotEmpty()) {
                    sendNotification(context, HadithsData.hadithForDay())
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Direct notification trigger failed: ${e.message}", e)
            }
        }
    }
}
