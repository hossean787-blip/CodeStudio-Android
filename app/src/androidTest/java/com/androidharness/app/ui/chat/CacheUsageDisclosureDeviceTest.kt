package com.androidharness.app.ui.chat

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidharness.app.R
import com.androidharness.app.data.ThemeMode
import com.androidharness.app.data.db.UsageEventEntity
import com.androidharness.app.ui.theme.HarnessTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheUsageDisclosureDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun findText(needle: String): AccessibilityNodeInfo? {
        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString()?.contains(needle) == true) return node
            for (i in 0 until node.childCount) {
                search(node.getChild(i))?.let { return it }
            }
            return null
        }
        return search(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun awaitText(needle: String): AccessibilityNodeInfo {
        repeat(40) {
            instrumentation.waitForIdleSync()
            findText(needle)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Expected visible text: $needle")
    }

    private fun clickClickableAncestor(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        while (current != null && !current.isClickable) current = current.parent
        assertNotNull("Disclosure must be accessible and clickable", current)
        assertTrue(current!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    @Test fun cacheTelemetryIsHiddenUntilExpandedAndCanBeClosedAgain() {
        val usage = listOf(UsageEventEntity(
            sessionId = "disclosure-test",
            providerName = "test",
            model = "test",
            inputTokens = 100,
            outputTokens = 10,
            cachedTokens = 0,
            cacheWriteTokens = 0,
            createdAt = 1,
            turnId = "t",
            cacheReported = true,
        ))
        ActivityScenario.launch(ChatListTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HarnessTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                        Surface { Column { CacheUsageFooter(usage) } }
                    }
                }
            }
            val show = context.getString(R.string.ui_show_cache_details)
            val hide = context.getString(R.string.ui_hide_cache_details)
            val disclosure = awaitText(show)
            assertNull("Normal cache misses must not appear as errors by default", findText("Cache missed"))
            clickClickableAncestor(disclosure)
            awaitText(hide)
            awaitText("Cache missed")
            clickClickableAncestor(awaitText(hide))
            awaitText(show)
            assertNull("Closing diagnostics must hide cache status again", findText("Cache missed"))
        }
    }
}
