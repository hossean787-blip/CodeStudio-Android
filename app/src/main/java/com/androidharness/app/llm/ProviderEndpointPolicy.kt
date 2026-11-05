package com.androidharness.app.llm

import java.net.URI
import java.util.Locale

/**
 * A saved provider endpoint can receive API keys and custom auth headers.
 * Reject cleartext remote endpoints and URL-embedded credentials before any
 * catalog probe or credential persistence. Loopback is needed for local Ollama.
 */
object ProviderEndpointPolicy {
    enum class Issue { INVALID, INSECURE, EMBEDDED_CREDENTIALS, UNSUPPORTED_COMPONENTS }

    fun issue(rawUrl: String): Issue? {
        val value = rawUrl.trim()
        if (value.isBlank() || value.any(Char::isWhitespace)) return Issue.INVALID
        val uri = try { URI(value) } catch (_: IllegalArgumentException) { return Issue.INVALID }
        if (!uri.isAbsolute || uri.isOpaque || uri.host.isNullOrBlank() ||
            uri.port == 0 || uri.port > 65535 || uri.port < -1) return Issue.INVALID
        if (uri.rawUserInfo != null) return Issue.EMBEDDED_CREDENTIALS
        if (uri.rawQuery != null || uri.rawFragment != null) return Issue.UNSUPPORTED_COMPONENTS
        val scheme = uri.scheme.lowercase(Locale.ROOT)
        if (scheme !in setOf("https", "http")) return Issue.INVALID
        val host = uri.host.removePrefix("[").removeSuffix("]").lowercase(Locale.ROOT)
        if (scheme == "http" && host !in setOf("localhost", "127.0.0.1", "::1"))
            return Issue.INSECURE
        return null
    }
}
