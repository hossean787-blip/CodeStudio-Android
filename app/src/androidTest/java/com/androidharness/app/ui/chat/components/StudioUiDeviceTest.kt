package com.androidharness.app.ui.chat.components

import android.content.res.Configuration
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidharness.app.R
import com.androidharness.app.core.ChatMessage
import com.androidharness.app.core.Role
import com.androidharness.app.core.ToolCallData
import com.androidharness.app.data.ThemeMode
import com.androidharness.app.ui.chat.ChatListTestActivity
import com.androidharness.app.ui.theme.HarnessTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioUiDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun visibleNodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
    }

    @Test fun persianHomeIsRtlAndCapturesRealVisualEvidence() {
        val context = instrumentation.targetContext
        val fa = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("fa"))
            setLayoutDirection(Locale.forLanguageTag("fa"))
        })
        ActivityScenario.launch(ChatListTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HarnessTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                        CompositionLocalProvider(LocalContext provides fa,
                            LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Surface { EmptyState(hasProvider = true, onSuggestion = {}, onAddProvider = {}) }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            val deadline = SystemClock.uptimeMillis() + 12_000
            while (SystemClock.uptimeMillis() < deadline &&
                visibleNodes().none { it.isVisibleToUser && it.text?.toString()?.contains("شروع سریع") == true }) {
                SystemClock.sleep(180)
            }
            assertTrue("New Persian quick-start section missing",
                visibleNodes().any { it.isVisibleToUser && it.text?.toString()?.contains("شروع سریع") == true })
            assertTrue("New Android suggestion missing",
                visibleNodes().any { it.text?.toString()?.contains("پروژه اندرویدی") == true })
            val dir = File(context.getExternalFilesDir(null), "issue8/ui-redesign").apply { mkdirs() }
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val file = File(dir, "home-fa-rtl-light.png")
                file.outputStream().use { assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
                assertTrue("Screenshot is empty", file.length() > 2048L)
            } finally {
                screenshot.recycle()
            }
        }
    }

    @Test fun expandedActivityNeverRendersPrivateModelReasoning() {
        val marker = "PRIVATE_REASONING_MUST_STAY_HIDDEN_UI_TEST"
        ActivityScenario.launch(ChatListTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HarnessTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                        Surface {
                            Box(Modifier.fillMaxSize()) {
                                ChatActivity(
                                    messages = listOf(ChatMessage(role = Role.ASSISTANT, thinking = marker, thinkingMs = 2_000)),
                                    autoExpand = false,
                                    showThinkingMetrics = false,
                                )
                            }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            assertTrue("Activity summary missing", visibleNodes().any {
                it.isVisibleToUser && it.text?.toString()
                    ?.contains(instrumentation.targetContext.getString(R.string.chat_thought_seconds, 2)) == true
            })
            assertFalse("Private thoughts visible before opening", visibleNodes().any {
                it.text?.toString()?.contains(marker) == true
            })
            visibleNodes().firstOrNull { it.isVisibleToUser && it.isClickable }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            instrumentation.waitForIdleSync()
            assertFalse("Private thoughts visible after expanding", visibleNodes().any {
                it.text?.toString()?.contains(marker) == true
            })
            assertFalse("Expanded activity rendered a duplicate private-thought heading", visibleNodes().any {
                it.isVisibleToUser &&
                    it.text?.toString() == instrumentation.targetContext.getString(R.string.chat_thought)
            })
        }
    }

    @Test fun persianResearchProgressUsesLocalizedLabelsAndRtl() {
        val context = instrumentation.targetContext
        val fa = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("fa"))
            setLayoutDirection(Locale.forLanguageTag("fa"))
        })
        val toolId = "research-progress-fixture"
        val messages = listOf(
            ChatMessage(role = Role.ASSISTANT,
                toolCalls = listOf(ToolCallData(toolId, "web_search", "{}"))),
            ChatMessage(role = Role.TOOL,
                text = "[sources this turn: 2/5, 3 remaining; pages read: 1/2 required]",
                toolCallId = toolId, toolName = "web_search"),
        )
        ActivityScenario.launch(ChatListTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HarnessTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                        CompositionLocalProvider(
                            LocalContext provides fa,
                            LocalLayoutDirection provides LayoutDirection.Rtl,
                        ) {
                            Surface {
                                Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
                                    ChatActivity(messages = messages, autoExpand = false,
                                        showThinkingMetrics = false)
                                }
                            }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            val sourceLabel = fa.getString(R.string.chat_activity_sources_progress, 2, 5)
            val readLabel = fa.getString(R.string.chat_activity_pages_read, 1)
            assertTrue("Persian source count not visible", visibleNodes().any {
                it.isVisibleToUser && it.text?.toString()?.contains(sourceLabel) == true
            })
            assertTrue("Persian page count not visible", visibleNodes().any {
                it.isVisibleToUser && it.text?.toString()?.contains(readLabel) == true
            })
            assertFalse("English research status leaked into Persian UI", visibleNodes().any {
                it.isVisibleToUser && (it.text?.toString()?.contains("/5 sources") == true ||
                    it.text?.toString()?.endsWith(" read") == true)
            })
            val dir = File(context.getExternalFilesDir(null), "issue8/ui-redesign").apply { mkdirs() }
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val evidence = File(dir, "chat-research-fa-rtl.png")
                evidence.outputStream().use {
                    assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
                assertTrue("RTL research screenshot missing", evidence.length() > 2048L)
            } finally {
                screenshot.recycle()
            }
        }
    }
}
