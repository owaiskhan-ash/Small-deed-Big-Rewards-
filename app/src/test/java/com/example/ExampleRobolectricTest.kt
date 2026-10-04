package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.HadithsData
import com.example.worker.DailyHadithWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Small Deeds, Big Rewards", appName)
  }

  @Test
  fun `launch MainActivity`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    assertNotNull(controller.get())
  }

  /**
   * The daily reminder must arm exactly one periodic work, and re-arming (which
   * happens on every app start) must not queue a second one -- that is the
   * [androidx.work.ExistingPeriodicWorkPolicy.KEEP] contract the production code
   * relies on to avoid resetting the 08:00 countdown.
   */
  @Test
  fun `daily reminder arms one periodic work and KEEP does not duplicate it`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    WorkManagerTestInitHelper.initializeTestWorkManager(context)

    DailyHadithWorker.scheduleDailyNotification(context)
    val workManager = WorkManager.getInstance(context)
    val first = workManager
      .getWorkInfosForUniqueWork(DailyHadithWorker.WORK_NAME).get()
    assertEquals("exactly one unique periodic work", 1, first.size)
    assertEquals(WorkInfo.State.ENQUEUED, first[0].state)

    DailyHadithWorker.scheduleDailyNotification(context)
    val second = workManager
      .getWorkInfosForUniqueWork(DailyHadithWorker.WORK_NAME).get()
    assertEquals("re-scheduling with KEEP must not duplicate the work", 1, second.size)
    assertEquals("KEEP must preserve the original work id", first[0].id, second[0].id)

    DailyHadithWorker.cancelDailyNotification(context)
    val cancelled = workManager
      .getWorkInfosForUniqueWork(DailyHadithWorker.WORK_NAME).get()
    assertEquals(WorkInfo.State.CANCELLED, cancelled[0].state)
  }

  /** Posting the reminder must surface a notification once permission is held. */
  @Test
  fun `sendNotification posts to the notification manager`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    shadowOf(context as android.app.Application)
      .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

    DailyHadithWorker.sendNotification(context, HadithsData.hadithForDay())

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val posted = shadowOf(manager).allNotifications
    assertEquals("one notification posted", 1, posted.size)
    assertEquals(DailyHadithWorker.NOTIFICATION_ID, posted[0].id)
  }
}
