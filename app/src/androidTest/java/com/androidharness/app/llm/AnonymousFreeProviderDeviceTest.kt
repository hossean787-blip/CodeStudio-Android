package com.androidharness.app.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidharness.app.core.ChatMessage
import com.androidharness.app.core.Role
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in external smoke: one public, explicitly free route and synthetic text only. */
@RunWith(AndroidJUnit4::class)
class AnonymousFreeProviderDeviceTest {
    @Test fun oneAnonymousFreeRequestReturnsTextThroughTheNativeStreamingAdapter(): Unit = runBlocking {
        assumeTrue("Live anonymous inference requires explicit test-run opt-in",
            InstrumentationRegistry.getArguments().getString("liveAnonymousFreeProvider") == "true")
        val original = HarnessProvider.pooledModels
        val report = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "issue8/anonymous-free-provider.json").apply { parentFile?.mkdirs() }
        // Fail closed if routing ever changes: no account, key, paid model, or alternate origin.
        // Mirror the production SSE transport: the first response headers can take
        // longer than OkHttp's default 10-second read timeout on a free queue.
        // withTimeout(60_000) below remains the hard end-to-end smoke-test deadline.
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
            val request = chain.request()
            check(request.url.host == "api.kilo.ai" && request.url.encodedPath == "/api/gateway/chat/completions")
            check(request.header("Authorization") == null)
            chain.proceed(request)
        }.build()
        assertEquals("Live SSE must not inherit OkHttp's default 10s read timeout", 0, client.readTimeoutMillis)
        assertEquals(30_000, client.connectTimeoutMillis)
        try {
            HarnessProvider.restoreModels(listOf(ModelEntry(HarnessProvider.DEFAULT_MODEL)))
            val events = withTimeout(60_000) {
                HarnessProvider.create(client).streamChat(HarnessProvider.config, HarnessProvider.KEYLESS, "",
                    listOf(ChatMessage(Role.USER, "Reply with the single word OK.")), emptyList(),
                    RequestOptions(maxOutputTokens = 512)).toList()
            }
            val failures = events.filterIsInstance<StreamEvent.Failure>()
            val text = events.filterIsInstance<StreamEvent.TextDelta>().joinToString("") { it.text }
            val passed = failures.isEmpty() && text.isNotBlank()
            report.writeText("""{"checked_at_utc":"${Instant.now()}","model":"kilo-auto/free","native_streaming_adapter":true,"returned_text":${text.isNotBlank()},"passed":$passed,"physical_device":false,"credentials_used":false,"requests":1}""")
            assertTrue("Anonymous free route did not return native streaming text; see test evidence", passed)
        } finally {
            HarnessProvider.restoreModels(original)
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        }
    }
}
