package com.androidharness.app.integrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationCatalogTest {
    @Test fun noDuplicateEntries() {
        val ids = IntegrationCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
    @Test fun onlyGitHubIsLabelledNativeAccountIntegration() {
        val native = IntegrationCatalog.entries.filter { it.path == IntegrationPath.GITHUB }
        assertEquals(listOf("github"), native.map { it.id })
    }
    @Test fun gmailAndDriveAreLimitedToSystemCapabilities() {
        assertEquals(IntegrationPath.EMAIL_COMPOSE, IntegrationCatalog.entries.single { it.id == "gmail" }.path)
        assertEquals(IntegrationPath.DOCUMENT_PICKER, IntegrationCatalog.entries.single { it.id == "drive" }.path)
    }
    @Test fun directShortcutIsAtTopWithoutHidingOtherApps() {
        val apps = IntegrationCatalog.selectedFirst("drive")
        assertEquals("drive", apps.first().id)
        assertEquals(IntegrationCatalog.entries.size, apps.size)
        assertTrue(IntegrationCatalog.selectedFirst("unknown") == IntegrationCatalog.entries)
    }
}
