package com.androidharness.app.ui.settings

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class FreeApiCatalogTest {
    @Test fun catalogHasUniqueEntriesAndOnlyOfficialHttpsInformationLinks() {
        val entries = FreeApiCatalog.entries
        assertTrue(entries.size >= 8)
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        entries.forEach {
            val uri = URI(it.officialUrl)
            assertEquals("https", uri.scheme)
            assertNull(uri.userInfo)
            assertNull(uri.query)
            assertNull(uri.fragment)
            assertTrue(uri.host.isNotBlank())
            assertFalse("No unlimited tier may be advertised", it.title.contains("unlimited", ignoreCase = true))
        }
    }

    @Test fun everyConnectButtonUsesExistingSecureProviderConfiguration() {
        val connectable = FreeApiCatalog.entries.filter { it.connectionPreset() != null }
        assertTrue(connectable.size >= 5)
        connectable.forEach { entry ->
            val brand = requireNotNull(entry.connectionPreset())
            assertEquals(ProviderBrands.filterNotNull().single { it.label == entry.providerBrand }.baseUrl, brand.baseUrl)
            assertEquals(ProviderBrands.filterNotNull().single { it.label == entry.providerBrand }.type, brand.type)
            assertNull(URI(brand.baseUrl).userInfo)
        }
        assertEquals("openrouter/free", FreeApiCatalog.entries.single { it.id == "openrouter" }.connectionPreset()?.suggestedModel)
        assertEquals("openai/gpt-oss-20b", FreeApiCatalog.entries.single { it.id == "groq" }.connectionPreset()?.suggestedModel)
    }

    @Test fun documentationOnlyEntriesCannotBeMisrepresentedAsConnections() {
        val docs = FreeApiCatalog.entries.filter { it.tier == FreeApiTier.DIRECTORY || it.tier == FreeApiTier.SETUP_REQUIRED }
        assertTrue(docs.isNotEmpty())
        assertTrue(docs.all { it.connectionPreset() == null })
        assertFalse(FreeApiCatalog.entries.single { it.id == "ollama" }.connectionPreset()!!.needsKey)
        assertFalse(FreeApiCatalog.entries.any { it.title.contains("no limit", ignoreCase = true) })
    }

    @Test fun localPresetStartsWithoutInvalidBlankKeyWhileCloudPresetRequiresOne() {
        val local = requireNotNull(FreeApiCatalog.entries.single { it.id == "ollama" }.connectionPreset())
        val cloud = requireNotNull(FreeApiCatalog.entries.single { it.id == "gemini" }.connectionPreset())
        val localPool = initialProviderKeyPool(null, local)
        assertEquals(emptyList<com.androidharness.app.llm.ProviderApiKey>(), localPool.keys)
        assertNull(localPool.error())
        val cloudPool = initialProviderKeyPool(null, cloud)
        assertEquals(1, cloudPool.keys.size)
        assertNotNull(cloudPool.error())
        val editing = com.androidharness.app.llm.ProviderConfig("saved", "saved", cloud.type, cloud.baseUrl, "model")
        assertTrue(initialProviderKeyPool(editing, local).keys.isEmpty())
    }

    @Test fun searchAndConnectedFilterDoNotAlterTheBank() {
        assertEquals(listOf("openrouter"), FreeApiCatalog.search("ROUTER").map { it.id })
        assertEquals(emptyList<FreeApiEntry>(), FreeApiCatalog.search("cloudflare", onlyConnectable = true))
        assertEquals(listOf("ollama"), FreeApiCatalog.search("", onlyNoCloudKey = true).map { it.id })
        assertEquals(listOf("gemini", "mistral"), FreeApiCatalog.search("", onlyNoCard = true).map { it.id })
        assertEquals(emptyList<FreeApiEntry>(), FreeApiCatalog.search("", onlyNoCard = true, onlyNoCloudKey = true))
        assertEquals(FreeApiCatalog.entries.size, FreeApiCatalog.search(" ").size)
        assertEquals(FreeApiCatalog.entries.size, FreeApiCatalog.search("").size)
    }
}
