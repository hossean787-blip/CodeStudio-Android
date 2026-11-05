package com.androidharness.app.llm

import org.junit.Assert.*
import org.junit.Test

class ProviderEndpointPolicyTest {
    @Test fun officialProvidersAndLoopbackOllamaRemainConfigurable() {
        listOf(
            "https://api.openai.com/v1",
            "https://api.anthropic.com",
            "https://generativelanguage.googleapis.com/v1beta",
            "https://api.deepseek.com/v1",
            "https://api.groq.com/openai/v1",
            "https://openrouter.ai/api/v1",
            "http://127.0.0.1:11434/v1",
            "http://localhost:11434/v1",
            "http://[::1]:11434/v1",
            "https://example.org/custom/v1",
        ).forEach { assertNull(it, ProviderEndpointPolicy.issue(it)) }
    }

    @Test fun aKeyCannotBeForwardedOverArbitraryCleartext() {
        listOf(
            "http://api.openai.com/v1",
            "http://192.168.1.20:11434/v1",
            "http://localhost.evil.example/v1",
            "http://127.0.0.2:11434/v1",
        ).forEach { assertEquals(it, ProviderEndpointPolicy.Issue.INSECURE, ProviderEndpointPolicy.issue(it)) }
    }

    @Test fun rejectsEmbeddedCredentialsAndUntrustedUriFeatures() {
        assertEquals(ProviderEndpointPolicy.Issue.EMBEDDED_CREDENTIALS,
            ProviderEndpointPolicy.issue("https://user:secret@example.org/v1"))
        assertEquals(ProviderEndpointPolicy.Issue.UNSUPPORTED_COMPONENTS,
            ProviderEndpointPolicy.issue("https://example.org/v1?token=secret"))
        assertEquals(ProviderEndpointPolicy.Issue.UNSUPPORTED_COMPONENTS,
            ProviderEndpointPolicy.issue("https://example.org/v1#part"))
        listOf("", "ftp://example.org/v1", "example.org/v1", "https:///v1", "https://example.org:0/v1",
            "https://example.org:99999/v1", "https://example.org/my path").forEach {
            assertEquals(it, ProviderEndpointPolicy.Issue.INVALID, ProviderEndpointPolicy.issue(it))
        }
    }
}
