package com.androidharness.app.film

import org.junit.Assert.*
import org.junit.Test

class FilmCutProjectTest {
    private fun project() = FilmCutProject(title = "نمای اول", sourceUri = "content://media/external/video/42",
        startMs = 1_000, endMs = 5_250)

    @Test fun validCutRoundTripsWithUnicodeAndExactMillisecondTimeline() {
        val original = project()
        assertNull(original.validationError(durationMs = 8_000))
        assertEquals(original, FilmCutProject.fromPortableJson(original.toPortableJson()))
        assertEquals(4_250L, original.endMs - original.startMs)
    }

    @Test fun boundaryAndSourceChecksRejectInvalidOrNonLocalCuts() {
        assertNotNull(project().copy(startMs = -1).validationError())
        assertNotNull(project().copy(endMs = 1_000).validationError())
        assertNotNull(project().copy(sourceUri = "https://example.com/stream?token=secret").validationError())
        assertNotNull(project().copy(endMs = 8_001).validationError(durationMs = 8_000))
        assertNotNull(project().copy(endMs = FilmCutProject.MAX_CLIP_MS + 1_001).validationError())
    }

    @Test fun importedProjectsCannotBypassValidationOrExceedBounds() {
        assertThrows(IllegalArgumentException::class.java) { FilmCutProject.fromPortableJson("x".repeat(16_385)) }
        assertThrows(IllegalArgumentException::class.java) {
            FilmCutProject.fromPortableJson("""{"version":2,"title":"x","sourceUri":"content://test/video","startMs":0,"endMs":1000}""")
        }
    }

    @Test fun projectJsonEscapesUserTextAndDoesNotContainApiCredentials() {
        val safe = project().copy(title = "نمای \"الف\"\\برش")
        val json = safe.toPortableJson()
        assertEquals(safe, FilmCutProject.fromPortableJson(json))
        assertFalse(json.contains("apiKey"))
        assertTrue(json.contains("sourceUri"))
    }
}
