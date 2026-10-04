package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  fun useAppContext() {
    // Context of the app under test.
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    // NOTE: this is the *applicationId*, not the namespace. The namespace is
    // `com.example`, but `targetContext.packageName` reports the id the app is
    // installed under -- the previous assertion compared against the namespace
    // and therefore failed on every device.
    assertEquals("com.aistudio.smalldeeds.kfkjqo", appContext.packageName)
  }
}
