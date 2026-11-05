package com.androidharness.app.llm

import com.androidharness.app.core.ChatMessage
import com.androidharness.app.core.Role
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class OpenAiSystemPromptTest {
    private val provider = OpenAiCompatProvider(okhttp3.OkHttpClient(), ProviderFactory.json)
    private val config = ProviderConfig("fixture", "Fixture", ProviderType.OPENAI_COMPAT, HarnessProvider.KILO_BASE_URL, HarnessProvider.DEFAULT_MODEL)

    @Test fun blankSystemPromptsAreOmittedInsteadOfSendingInvalidEmptyMessages() {
        for (system in listOf("", "  \n\t")) {
            val body = provider.buildRequestBody(config, system, listOf(ChatMessage(Role.USER, "Reply OK")), emptyList(), RequestOptions())
            val messages = body.getValue("messages").jsonArray
            assertEquals(1, messages.size)
            assertEquals("user", messages.single().jsonObject.getValue("role").jsonPrimitive.content)
            assertEquals("Reply OK", messages.single().jsonObject.getValue("content").jsonPrimitive.content)
        }
    }

    @Test fun realSystemInstructionsAndOpenRouterCacheMetadataRemainUnchanged() {
        val text = "دستور واقعی صبا"
        val plain = provider.buildRequestBody(config, text, listOf(ChatMessage(Role.USER, "Hello")), emptyList(), RequestOptions())
        assertEquals(text, plain.getValue("messages").jsonArray.first().jsonObject.getValue("content").jsonPrimitive.content)
        val routed = config.copy(baseUrl = "https://openrouter.ai/api/v1", model = "anthropic/claude-sonnet")
        val cached = provider.buildRequestBody(routed, text, listOf(ChatMessage(Role.USER, "Hello")), emptyList(), RequestOptions())
        val block = cached.getValue("messages").jsonArray.first().jsonObject.getValue("content").jsonArray.single().jsonObject
        assertEquals(text, block.getValue("text").jsonPrimitive.content)
        assertEquals("ephemeral", block.getValue("cache_control").jsonObject.getValue("type").jsonPrimitive.content)
        val empty = provider.buildRequestBody(routed, "", listOf(ChatMessage(Role.USER, "Hello")), emptyList(), RequestOptions())
        assertEquals("user", empty.getValue("messages").jsonArray.first().jsonObject.getValue("role").jsonPrimitive.content)
    }
}
