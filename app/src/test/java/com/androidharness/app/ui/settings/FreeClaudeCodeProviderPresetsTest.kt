package com.androidharness.app.ui.settings

import com.androidharness.app.llm.ProviderType
import org.junit.Assert.*
import org.junit.Test

class FreeClaudeCodeProviderPresetsTest {
    @Test fun upstreamApiPresetsAreReachableFromAndroidProviderPicker() {
        val presets = ProviderBrands.filterNotNull()
        for (name in listOf("NVIDIA NIM", "Moonshot Kimi", "Fireworks AI", "Cerebras",
            "Ollama Cloud", "xAI Grok", "Hugging Face", "SiliconFlow", "ZenMux")) {
            val entry = presets.single { it.label == name }
            assertEquals(ProviderType.OPENAI_COMPAT, entry.type)
            assertTrue(entry.baseUrl.startsWith("https://"))
            assertTrue(entry.needsKey)
        }
    }

    @Test fun connectionPresetsDoNotIncludeCredentialsOrDuplicateEndpoints() {
        val presets = ProviderBrands.filterNotNull()
        assertEquals(presets.size, presets.map { it.label }.distinct().size)
        assertEquals(presets.size, presets.map { it.type to it.baseUrl.trimEnd('/') }.distinct().size)
        assertTrue(presets.none { it.baseUrl.contains('@') || it.baseUrl.contains('?') })
    }
}
