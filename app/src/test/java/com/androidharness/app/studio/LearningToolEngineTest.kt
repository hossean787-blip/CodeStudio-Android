package com.androidharness.app.studio

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LearningToolEngineTest {
    @Test fun everyApprovedToolProducesBoundedOfflineOutput() {
        StudioTool.entries.forEach { tool ->
            val result = LearningToolEngine.run(tool, "نمونه", "پرسش | پاسخ\nبخش دوم", Instant.EPOCH)
            assertTrue(tool.name, result.content.isNotBlank())
            assertTrue(tool.name, result.extension in setOf("md", "srt", "ics"))
        }
        assertEquals(23, StudioTool.entries.size)
    }

    @Test fun subtitleIsValidSequentialSrt() {
        val result = LearningToolEngine.run(StudioTool.SUBTITLES, "", "سلام\nدنیا", Instant.EPOCH)
        assertEquals("srt", result.extension)
        assertTrue(result.content.contains("1\n00:00:00,000 --> 00:00:04,000\nسلام"))
        assertTrue(result.content.endsWith("\n"))
    }

    @Test fun calendarHasStableUniqueEventsAndNoNetworkClaim() {
        val result = LearningToolEngine.run(StudioTool.STUDY_CALENDAR, "درس", "مرور فصل یک\nآزمون", Instant.EPOCH)
        assertTrue(result.content.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0"))
        assertEquals(2, "BEGIN:VEVENT".toRegex().findAll(result.content).count())
        assertTrue(result.content.endsWith("END:VCALENDAR\r\n"))
    }

    @Test fun securityScannerFlagsSecretsAndPlainHttp() {
        val output = LearningToolEngine.run(StudioTool.CODE_SECURITY, "", "api_key = abcdefgh\nhttp://example.test").content
        assertTrue(output.contains("کلید یا توکن احتمالی"))
        assertTrue(output.contains("HTTP بدون TLS"))
        assertTrue(output.contains("جایگزین بازبینی امنیتی"))
    }

    @Test fun rejectsBlankAndOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) { LearningToolEngine.run(StudioTool.MIND_MAP, "", " ") }
        assertThrows(IllegalArgumentException::class.java) { LearningToolEngine.run(StudioTool.MIND_MAP, "", "x".repeat(100_001)) }
    }
}
