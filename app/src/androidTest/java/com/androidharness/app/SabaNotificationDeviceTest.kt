package com.androidharness.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidharness.app.ui.common.localizedOptionLabel
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SabaNotificationDeviceTest {
    @Test fun activeForegroundNotificationUsesSabaBrandAndLocalizedPrivateStatus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation
            .grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val fa = target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("fa")); setLayoutDirection(Locale.forLanguageTag("fa"))
        })
        val statusResources = mapOf("Working…" to R.string.ui_working, "Thinking…" to R.string.ui_thinking,
            "Writing response…" to R.string.ui_writing_response, "Generating code…" to R.string.ui_generating_code,
            "Waiting for your approval" to R.string.ui_waiting_for_your_approval,
            "Waiting for your answer" to R.string.ui_waiting_for_your_answer)
        statusResources.forEach { (status, resource) ->
            assertEquals(fa.getString(resource), fa.localizedOptionLabel(status))
            assertTrue(fa.localizedOptionLabel(status).any { it in '\u0600'..'\u06ff' })
        }
        val testSessionId = "saba-notification-question-smoke"
        val savedStatus = RuntimeNotifier.status.value
        val savedActive = RuntimeNotifier.active.value
        try {
            RuntimeNotifier.setActive(true)
            RuntimeNotifier.update("Working…")
            AgentService.startPersistent(target)
            val manager = target.getSystemService(NotificationManager::class.java)
            val deadline = SystemClock.uptimeMillis() + 10_000
            var notification: Notification? = null
            while (SystemClock.uptimeMillis() < deadline) {
                notification = manager.activeNotifications.firstOrNull { it.id == AgentService.NOTIFICATION_ID }?.notification
                if (notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() == target.localizedOptionLabel("Working…")) break
                SystemClock.sleep(100)
            }
            val actual = requireNotNull(notification) { "Active foreground notification missing" }
            assertEquals(target.getString(R.string.app_name), actual.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(target.localizedOptionLabel("Working…"), actual.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals(Notification.VISIBILITY_PRIVATE, actual.visibility)
            listOf(
                AgentService.CHANNEL_ID,
                AgentService.RESULTS_CHANNEL_ID,
                AgentService.ALERT_CHANNEL_ID,
            ).forEach { id ->
                val channel = requireNotNull(manager.getNotificationChannel(id)) { "Missing notification channel: $id" }
                assertEquals(Notification.VISIBILITY_PRIVATE, channel.lockscreenVisibility)
            }

            RuntimeNotifier.setSessionPrompts(
                testSessionId,
                listOf(
                    PendingPrompt(
                        sessionId = testSessionId,
                        kind = PendingPrompt.Kind.QUESTION,
                        sessionTitle = "Saba QA",
                        headline = "Choose an answer",
                    ),
                ),
            )
            var question: Notification? = null
            val promptDeadline = SystemClock.uptimeMillis() + 10_000
            while (SystemClock.uptimeMillis() < promptDeadline) {
                question = manager.activeNotifications.firstOrNull {
                    it.tag == testSessionId && it.id == AgentService.ALERT_NOTIFICATION_ID
                }?.notification
                if (question != null) break
                SystemClock.sleep(100)
            }
            val alert = requireNotNull(question) { "Question alert missing" }
            assertEquals(Notification.VISIBILITY_PRIVATE, alert.visibility)
            assertEquals(
                target.getString(R.string.notification_quick_reply),
                alert.actions.last().title.toString(),
            )
        } finally {
            RuntimeNotifier.setSessionPrompts(testSessionId, emptyList())
            RuntimeNotifier.setActive(savedActive)
            RuntimeNotifier.update(savedStatus)
        }
    }
}
