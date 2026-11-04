package com.androidharness.app.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSourceCatalogTest {
    @Test fun allThirteenSourcesHaveStableUniquePins() {
        val sources = AiSourceCatalog.projects
        assertEquals(13, sources.size)
        assertEquals(13, sources.map { it.id }.distinct().size)
        assertEquals(13, sources.map { it.repository }.distinct().size)
        sources.forEach { source ->
            assertTrue(source.id.matches(Regex("[a-z0-9-]+")))
            assertTrue(source.repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
            assertTrue(source.pinnedCommit.matches(Regex("[0-9a-f]{40}")))
            assertEquals("https://github.com/${source.repository}/tree/${source.pinnedCommit}", source.pinnedSourceUrl)
            assertTrue(source.license.isNotBlank())
            assertTrue(source.descriptionFa.isNotBlank())
            assertTrue(source.descriptionEn.isNotBlank())
        }
    }

    @Test fun restrictedLicensesAreNotMistakenForPermissive() {
        val byId = AiSourceCatalog.projects.associateBy { it.id }
        assertEquals("AGPL-3.0", byId.getValue("lyra-code").license)
        assertEquals("GPL-3.0", byId.getValue("antigem").license)
        assertEquals("GPL-3.0", byId.getValue("mobilekinetic-agent").license)
        assertEquals("UNVERIFIED", byId.getValue("syntac").license)
        assertEquals("MIT", byId.getValue("pocket-pi").license)
        assertEquals("RESTRICTED-PROPRIETARY-DERIVED", byId.getValue("openclaude-android").license)
    }
}
