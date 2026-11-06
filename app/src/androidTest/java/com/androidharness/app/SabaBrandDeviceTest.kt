package com.androidharness.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Tests installed launcher and splash resources, not mockup screenshots. */
@RunWith(AndroidJUnit4::class)
class SabaBrandDeviceTest {
    @Test fun installedAppUsesSabaIdentityAndActualMonogram() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.startsWith("ir.sabagostar.sabastudio"))
        assertEquals("صبا استودیو", context.packageManager.getApplicationLabel(context.applicationInfo).toString())
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        val launcher = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        assertNotNull(launcher)
        if (Build.VERSION.SDK_INT >= 26) assertTrue(launcher is AdaptiveIconDrawable)
        val vector = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
        val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        vector.setBounds(0, 0, 108, 108)
        vector.draw(Canvas(bitmap))
        var colored = 0
        val colors = setOf(Color.rgb(0x40, 0xD5, 0xF2), Color.rgb(0xF6, 0xFC, 0xFF))
        for (y in 0 until 108) for (x in 0 until 108) if (bitmap.getPixel(x, y) in colors) colored++
        bitmap.recycle()
        assertTrue("Saba icon missing or not rendered", colored > 80)
    }

    @Test fun launchWindowUsesBrandedSplash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val themed = android.view.ContextThemeWrapper(context, R.style.Theme_AndroidHarness)
        val attr = if (Build.VERSION.SDK_INT >= 31)
            android.R.attr.windowSplashScreenAnimatedIcon else android.R.attr.windowBackground
        val expected = if (Build.VERSION.SDK_INT >= 31)
            R.drawable.ic_launcher_foreground else R.drawable.saba_launch_window
        val attrs = themed.obtainStyledAttributes(intArrayOf(attr))
        try { assertEquals(expected, attrs.getResourceId(0, 0)) }
        finally { attrs.recycle() }
    }
    /** Regression: catch the real-device defect where setup/home showed a black >_ prompt. */
    @Test fun actualComposeBrandMarkMatchesTheLauncherColors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        androidx.test.core.app.ActivityScenario.launch(
            com.androidharness.app.ui.chat.ChatListTestActivity::class.java
        ).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    com.androidharness.app.ui.theme.HarnessTheme(
                        themeMode = com.androidharness.app.data.ThemeMode.LIGHT,
                        dynamicColor = false,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            com.androidharness.app.ui.common.SabaBrandMark(104.dp)
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(350)
            instrumentation.waitForIdleSync()
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                var cyan = 0
                for (y in screenshot.height / 3 until screenshot.height * 2 / 3) {
                    for (x in screenshot.width / 4 until screenshot.width * 3 / 4) {
                        val pixel = screenshot.getPixel(x, y)
                        val red = Color.red(pixel)
                        val green = Color.green(pixel)
                        val blue = Color.blue(pixel)
                        if (red in 45..85 && green in 190..238 && blue in 220..255) cyan++
                    }
                }
                assertTrue("In-app Saba brand is not rendered in cyan; terminal icon may still be displayed", cyan > 60)
            } finally {
                screenshot.recycle()
            }
        }
    }

}
