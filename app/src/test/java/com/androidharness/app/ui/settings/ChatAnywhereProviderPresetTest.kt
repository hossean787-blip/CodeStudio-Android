package com.androidharness.app.ui.settings

import com.androidharness.app.llm.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAnywhereProviderPresetTest {
    @Test fun chatAnywhereIsSelectableWithOpenAiCompatibleTransport() {
        val brand = ProviderBrands.filterNotNull().single { it.label == "ChatAnywhere (GPT API Free)" }
        assertEquals(ProviderType.OPENAI_COMPAT, brand.type)
        assertEquals("https://api.chatanywhere.org/v1", brand.baseUrl)
        assertTrue(brand.needsKey)
        assertFalse(brand.baseUrl.contains('@'))
        assertFalse(brand.baseUrl.contains('?'))
    }

    @Test fun mainlandEndpointIsConfiguredSeparatelyAndRequiresUserKey() {
        val brand = ProviderBrands.filterNotNull().single { it.label == "ChatAnywhere (China)" }
        assertEquals(ProviderType.OPENAI_COMPAT, brand.type)
        assertEquals("https://api.chatanywhere.tech/v1", brand.baseUrl)
        assertTrue(brand.needsKey)
    }

    @Test fun bothThirdPartyAndOfficialOpenAiAreAvailableSeparately() {
        val brands = ProviderBrands.filterNotNull()
        assertEquals(1, brands.count { it.label == "ChatAnywhere (GPT API Free)" })
        assertEquals(1, brands.count { it.label == "OpenAI" })
        assertEquals(brands.size, brands.map { it.type to it.baseUrl.trimEnd('/') }.distinct().size)
    }
}
