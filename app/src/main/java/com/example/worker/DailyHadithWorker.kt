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
import kotlin.random.Random

class DailyHadithWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val hadiths = HadithsData.hadiths
            if (hadiths.isNotEmpty()) {
                val randomIndex = Random.nextInt(hadiths.size)
                val hadith = hadiths[randomIndex]
                sendHadithNotification(hadith)
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error showing daily hadith notification", e)
            Result.retry()
        }
    }

    private fun sendHadithNotification(hadith: Hadith) {
        sendNotification(context, hadith)
    }

    private fun createNotificationChannelIfNeeded() {
        createChannel(context)
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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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

            // Fallback icon check
            val iconResId = R.drawable.ic_stat_hadith

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconResId)
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
                    ExistingPeriodicWorkPolicy.UPDATE,
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
         * Triggers an immediate notification directly and via WorkManager.
         */
        fun triggerImmediateNotification(context: Context) {
            try {
                val hadiths = HadithsData.hadiths
                if (hadiths.isNotEmpty()) {
                    val hadith = hadiths.random()
                    sendNotification(context, hadith)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Direct notification trigger failed: ${e.message}", e)
            }
        }
    }
}
