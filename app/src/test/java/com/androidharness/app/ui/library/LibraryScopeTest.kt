package com.androidharness.app.ui.library

import org.junit.Assert.*
import org.junit.Test

class LibraryScopeTest {
    @Test fun sameProjectGetsStablePrivateFile() {
        val a = LibraryScope.fileName("project-a")
        assertEquals(a, LibraryScope.fileName("project-a"))
        assertTrue(a.startsWith("saba_private_sources_"))
        assertTrue(a.endsWith(".json"))
        assertNotEquals(LibraryScope.LEGACY_FILE_NAME, a)
    }

    @Test fun twoProjectsNeverShareAnIndexPath() {
        assertNotEquals(LibraryScope.fileName("project-a"), LibraryScope.fileName("project-b"))
    }

    @Test fun idsNeverEscapePrivateDirectory() {
        val file = LibraryScope.fileName("../other-project")
        assertFalse(file.contains("/"))
        assertFalse(file.contains(".."))
        assertFalse(file.contains("other-project"))
    }

    @Test fun emptyScopeIsRejected() {
        try {
            LibraryScope.fileName(" ")
            fail("Missing project scope must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
