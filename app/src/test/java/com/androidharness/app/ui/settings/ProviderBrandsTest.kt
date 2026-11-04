package com.androidharness.app.ui.settings

import com.androidharness.app.llm.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderBrandsTest {
    private val brands = ProviderBrands.filterNotNull()

    @Test fun a2agentSitsAtTheVeryTopAndIsTheOnlyPinnedBrand() {
        assertEquals("A2Agent", brands.first().label)
        assertEquals(listOf("A2Agent"), brands.filter { it.pinned }.map { it.label })
        assertTrue(brands.drop(1).any { it.label == "OpenRouter" })
        assertTrue(brands.drop(1).any { it.label == "ChatAnywhere (GPT API Free)" })
    }

    @Test fun a2agentUsesTheGatewayRootThatServesChatCompletionsAndModelsApi() {
        val a2agent = brands.single { it.label == "A2Agent" }
        assertEquals(ProviderType.OPENAI_COMPAT, a2agent.type)
        // The request URL is baseUrl + /chat/completions, so the version prefix
        // belongs in the stored base URL.
        assertEquals("https://api.a2agent.me/v1", a2agent.baseUrl)
        assertTrue(a2agent.needsKey)
        assertNotNull("A new brand needs its badge", a2agent.badge)
    }

    @Test fun openAiCompatibleBrandsStoreCompleteProviderSpecificApiRoots() {
        // OpenAI-compatible is a wire format, not a promise of a /v1 URL.
        // Keep exceptional roots exact so a missing prefix still fails.
        val exceptionalRoots = mapOf(
            "Z.ai Coding" to "https://api.z.ai/api/coding/paas/v4",
            "Z.ai API" to "https://api.z.ai/api/paas/v4",
            "DeepInfra" to "https://api.deepinfra.com/v1/openai",
            "Opper" to "https://api.opper.ai/v3/compat",
            "Kilo AI Gateway" to "https://api.kilo.ai/api/gateway",
            "Gemini (OpenAI Compatible)" to "https://generativelanguage.googleapis.com/v1beta/openai",
        )
        brands.filter { it.type == ProviderType.OPENAI_COMPAT }.forEach {
            val expected = exceptionalRoots[it.label]
            if (expected != null) assertEquals(it.label, expected, it.baseUrl.trimEnd('/'))
            else assertTrue("${it.label} must end at /v1", it.baseUrl.trimEnd('/').endsWith("/v1"))
            val uri = java.net.URI(it.baseUrl)
            assertTrue("${it.label} must not embed credentials or query parameters",
                uri.userInfo == null && uri.query == null && uri.fragment == null)
        }
        assertTrue(exceptionalRoots.keys.all { name -> brands.any { it.label == name } })
    }

    @Test fun curatedBrandsStayListableDespiteTheFetchedDirectory() {
        // These are compiled in, so a fetched models.dev refresh can never
        // remove or reorder them, and every row needs a unique key.
        assertEquals(brands.size, brands.map { it.label }.distinct().size)
        assertTrue(brands.size >= 11)
    }
}
