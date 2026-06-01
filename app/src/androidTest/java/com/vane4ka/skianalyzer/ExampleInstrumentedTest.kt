/**
 * Auto-generated smoke test left in place from the Android Studio template.
 *
 * Runs on a device/emulator under [AndroidJUnit4] and only verifies that the
 * app-under-test context's `packageName` matches `com.example.myapplication`.
 * Kept as a minimal sanity check that the instrumented test harness wires
 * up correctly; real UI assertions live in [MistakesScreenTest].
 */

package com.vane4ka.skianalyzer

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

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
        assertEquals("com.example.myapplication", appContext.packageName)
    }
}