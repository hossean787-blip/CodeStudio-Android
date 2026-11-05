package com.androidharness.app.llm

import com.androidharness.app.core.ChatMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class RotatingKeyProviderTest {
    private val config = ProviderConfig("pool", "Fixture", ProviderType.OPENAI_COMPAT, "https://provider.invalid", "model")
    private fun pool(rotate: Boolean = true) = ProviderKeyPool(listOf(ProviderApiKey("a", value = "alpha-key"), ProviderApiKey("b", value = "beta-key")), rotate)
    private fun provider(block: suspend FlowCollector<StreamEvent>.(String) -> Unit) = object : LlmProvider {
        override fun streamChat(config: ProviderConfig, apiKey: String, systemPrompt: String, messages: List<ChatMessage>, tools: List<ToolSchema>, options: RequestOptions) = flow { block(apiKey) }
    }

    @Test fun requestsUseEnabledKeysInTurnWithoutMutatingTheSavedPool() = runBlocking {
        val keys = mutableListOf<String>(); val saved = pool()
        val rotating = RotatingKeyProvider(provider { key -> keys += key; emit(StreamEvent.Done()) }, { saved })
        repeat(3) { rotating.streamChat(config, "stale-caller-key", "", emptyList(), emptyList(), RequestOptions()).toList() }
        assertEquals(listOf("alpha-key", "beta-key", "alpha-key"), keys)
        assertEquals(saved, pool())
    }

    @Test fun rejectedAuthenticationCanUseAnotherSavedCredentialBeforeOutput() = runBlocking {
        for (rejected in listOf(StreamEvent.Failure("rejected", 401),
            StreamEvent.Failure("rejected", errorCode = "authentication_error"))) {
            val keys = mutableListOf<String>()
            val rotating = RotatingKeyProvider(provider { key ->
                keys += key
                if (key == "alpha-key") emit(rejected)
                else { emit(StreamEvent.TextDelta("Recovered")); emit(StreamEvent.Done()) }
            }, { pool(false) })
            val events = rotating.streamChat(config, "ignored", "", emptyList(), emptyList(), RequestOptions()).toList()
            assertEquals(listOf("alpha-key", "beta-key"), keys)
            assertEquals(1, events.filterIsInstance<StreamEvent.Retrying>().size)
            assertEquals("Recovered", events.filterIsInstance<StreamEvent.TextDelta>().single().text)
            assertTrue(events.none { it is StreamEvent.Failure })
        }
    }

    @Test fun quotasCreditsForbiddenAndRegionsNeverRetryOnAnotherKey() = runBlocking {
        val rejections = listOf(
            StreamEvent.Failure("credits", 402), StreamEvent.Failure("forbidden", 403),
            StreamEvent.Failure("rate limited", 429),
            StreamEvent.Failure("quota", 429, errorCode = "insufficient_quota"),
            StreamEvent.Failure("region", 403, errorCode = "unsupported_country_region_territory"),
            StreamEvent.Failure("quota", errorCode = "resource_exhausted"),
            StreamEvent.Failure("quota", errorCode = "quota_exceeded"),
            StreamEvent.Failure("credit", errorCode = "credit_balance_too_low"),
            StreamEvent.Failure("region", errorCode = "unsupported_region"),
            StreamEvent.Failure("mixed", 401, errorCode = "insufficient_quota"))
        for (rejection in rejections) for (batched in listOf(false, true)) {
            val keys = mutableListOf<String>()
            val rotating = RotatingKeyProvider(provider { key ->
                keys += key
                val failure = rejection.copy(message = "${rejection.message}: alpha-key / beta-key")
                emit(if (batched) StreamEvent.Batch(listOf(StreamEvent.Batch(listOf(failure)))) else failure)
            }, { pool(false) })
            val events = rotating.streamChat(config, "ignored", "", emptyList(), emptyList(), RequestOptions()).toList()
            assertEquals(listOf("alpha-key"), keys)
            assertTrue(events.none { it is StreamEvent.Retrying })
            val failure = events.filterIsInstance<StreamEvent.Failure>().single()
            assertEquals(rejection.code, failure.code)
            assertEquals(rejection.errorCode, failure.errorCode)
            assertFalse(failure.message.contains("alpha-key")); assertFalse(failure.message.contains("beta-key"))
        }
    }

    @Test fun outputAndToolOrUsageEventsPreventReplayingARequestOnAnotherKey() = runBlocking {
        for (output in listOf(StreamEvent.TextDelta("partial"), StreamEvent.ThinkingDelta("thinking"),
            StreamEvent.ToolCallDelta("call", "shell", "{}"), StreamEvent.ToolCallBatch(emptyList()), StreamEvent.Usage(1, 1), StreamEvent.Done())) {
            var calls = 0
            val rotating = RotatingKeyProvider(provider { calls++; emit(output); emit(StreamEvent.Failure("limited", 429)) }, { pool(false) })
            val events = rotating.streamChat(config, "ignored", "", emptyList(), emptyList(), RequestOptions()).toList()
            assertEquals(1, calls)
            assertEquals(1, events.filterIsInstance<StreamEvent.Failure>().size)
            assertTrue(events.none { it is StreamEvent.Retrying })
        }
    }

    @Test fun exhaustionIsBoundedAndFailureMessagesNeverRevealAnySavedKey() = runBlocking {
        var calls = 0
        val rotating = RotatingKeyProvider(provider { calls++; emit(StreamEvent.Failure("alpha-key / beta-key", 401)) }, { pool(false) })
        val events = rotating.streamChat(config, "ignored", "", emptyList(), emptyList(), RequestOptions()).toList()
        assertEquals(2, calls)
        val failure = events.filterIsInstance<StreamEvent.Failure>().single()
        assertFalse(failure.message.contains("alpha-key")); assertFalse(failure.message.contains("beta-key"))
        assertEquals(401, failure.code)
    }

    @Test fun disabledKeysAndMalformedRequestsDoNotTriggerRecovery() = runBlocking {
        var calls = 0
        val delegate = provider { calls++; emit(StreamEvent.Failure("bad input", 400)) }
        val off = pool().copy(keys = pool().keys.map { it.copy(enabled = false) })
        assertEquals(1, RotatingKeyProvider(delegate, { off }).streamChat(config, "stale", "", emptyList(), emptyList(), RequestOptions()).toList().size)
        assertEquals(0, calls)
        RotatingKeyProvider(delegate, { pool(false) }).streamChat(config, "ignored", "", emptyList(), emptyList(), RequestOptions()).toList()
        assertEquals(1, calls)
    }

    @Test fun batchedFailuresRecoverOnlyWhenNoEarlierBatchedOutputExists() = runBlocking {
        for (prefix in listOf(emptyList(), listOf(StreamEvent.TextDelta("partial")), listOf(StreamEvent.ToolCallDelta("call", "shell", "{}")))) {
            var calls = 0
            val rotating = RotatingKeyProvider(provider { key ->
                calls++
                if (key == "alpha-key") emit(StreamEvent.Batch(listOf(StreamEvent.Batch(prefix + StreamEvent.Failure("authentication rejected", 401)))))
                else emit(StreamEvent.TextDelta("recovered"))
            }, { pool(false) })
            val events = rotating.streamChat(config, "ignored", "", emptyList(), emptyList(), RequestOptions()).toList()
            assertEquals(if (prefix.isEmpty()) 2 else 1, calls)
            assertEquals(prefix.isNotEmpty(), events.any { it is StreamEvent.Failure })
            if (prefix.isNotEmpty()) assertEquals(prefix.single(), events.first())
        }
    }

    @Test fun cancellationCancelsTheActiveRequestWithoutTryingAnotherKey() = runBlocking {
        val started = CompletableDeferred<Unit>(); var cancelled = false; var calls = 0
        val rotating = RotatingKeyProvider(provider {
            calls++; started.complete(Unit)
            try { awaitCancellation() } finally { cancelled = true }
        }, { pool() })
        val job = launch { rotating.streamChat(config, "ignored", "", emptyList(), emptyList(), RequestOptions()).collect() }
        started.await(); job.cancelAndJoin()
        assertTrue(cancelled); assertEquals(1, calls)
    }
}
