package com.androidharness.app.ui.study

import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidharness.app.R
import com.androidharness.app.data.ThemeMode
import com.androidharness.app.ui.chat.ChatListTestActivity
import com.androidharness.app.ui.theme.HarnessTheme
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Compose screen, synthetic notes only; asserts the visible Persian answer and screenshot. */
@RunWith(AndroidJUnit4::class)
class StudyAssistantDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun visibleNodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
    }
    private fun waitForText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val nodes = visibleNodes()
            if (nodes.any { it.isVisibleToUser && it.text?.toString()?.contains(text) == true }) return
            nodes.firstOrNull { it.isVisibleToUser && it.isScrollable }
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(150)
        }
        error("Missing Persian study practice text: $text")
    }
    @Test fun persianStudyPracticeGeneratesActualFlashcardsWithoutNetwork() {
        val target = instrumentation.targetContext
        val fa = target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("fa"))
            setLayoutDirection(Locale.forLanguageTag("fa"))
        })
        ActivityScenario.launch(ChatListTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HarnessTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                        CompositionLocalProvider(
                            LocalContext provides fa,
                            LocalLayoutDirection provides LayoutDirection.Rtl,
                        ) {
                            Surface { StudyAssistantScreen(onBack = {}) }
                        }
                    }
                }
            }
            waitForText(fa.getString(R.string.study_title))
            val deadline = SystemClock.uptimeMillis() + 15_000
            var inserted = false
            while (SystemClock.uptimeMillis() < deadline && !inserted) {
                val editable = visibleNodes().firstOrNull { it.isVisibleToUser && it.isEditable }
                if (editable != null) inserted = editable.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            "حسابرسی: بررسی مستقل اطلاعات مالی")
                    },
                )
                if (!inserted) SystemClock.sleep(150)
            }
            assertTrue("The study note field must be editable", inserted)
            instrumentation.waitForIdleSync()
            // The IME can cover the action on smaller emulator screens. Scroll until
            // its semantics node is visible and clickable rather than assuming it is in view.
            val clickDeadline = SystemClock.uptimeMillis() + 15_000
            var clicked = false
            while (!clicked && SystemClock.uptimeMillis() < clickDeadline) {
                val nodes = visibleNodes()
                var generate = nodes.firstOrNull {
                    it.text?.toString() == fa.getString(R.string.study_generate)
                }
                while (generate != null && !generate.isClickable) generate = generate.parent
                if (generate?.isVisibleToUser == true && generate.isEnabled) {
                    clicked = generate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                if (!clicked) {
                    nodes.filter { it.isScrollable }.forEach {
                        it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    }
                    SystemClock.sleep(150)
                }
            }
            assertTrue("The Generate button must be clickable after scrolling", clicked)
            waitForText("پاسخ: بررسی مستقل اطلاعات مالی")
            val directory = File(target.getExternalFilesDir(null), "issue8/study-ui").apply { mkdirs() }
            instrumentation.uiAutomation.takeScreenshot().also { bitmap ->
                val screenshot = File(directory, "study-fa-light-flashcards.png")
                screenshot.outputStream().use {
                    assertTrue("Screenshot PNG encoding failed", bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
                assertTrue("Screenshot PNG was empty", screenshot.length() > 1024L)
                bitmap.recycle()
            }
        }
    }
}
