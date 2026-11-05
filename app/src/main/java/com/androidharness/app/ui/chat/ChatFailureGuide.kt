package com.androidharness.app.ui.chat

import java.util.Locale

/** A deliberately low-information diagnosis: raw provider failures can contain secrets. */
internal enum class ChatFailureKind {
    AUTH, FORBIDDEN, REGION, CREDITS, RATE_LIMIT, MODEL, TIMEOUT, SERVER, TLS, NETWORK, OTHER
}

internal data class ChatFailureGuide(val kind: ChatFailureKind, val httpStatus: Int?) {
    /**
     * Exports only fixed enums and a status. Never copy exception messages, endpoint URLs,
     * model IDs, prompts, workspace paths, provider names, request headers or credentials.
     */
    fun safeReport(version: String, network: ChatNetworkState): String = buildString {
        appendLine("Saba Studio - Chat recovery")
        appendLine("App: " + version.takeWhile { it.isLetterOrDigit() || it in ".-+" }.take(40))
        appendLine("Failure: " + kind.name)
        appendLine("HTTP: " + (httpStatus?.toString() ?: "not available"))
        appendLine("Network: " + network.name)
        append("No messages, credentials, identifiers, URLs, file paths or raw logs included.")
    }
}

internal enum class ChatNetworkState { VALIDATED, NOT_VALIDATED, OFFLINE, UNKNOWN }

/**
 * Only a clearly formatted HTTP status is extracted. Numbers inside URLs, tokens,
 * account identifiers and free-form errors must never be mistaken for HTTP codes.
 * "403" by itself is not proof of region blocking.
 */
internal fun classifyChatFailure(raw: String): ChatFailureGuide {
    val status = Regex("""(?i)\bHTTP\s+([1-5]\d{2})\b""")
        .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val low = raw.lowercase(Locale.ROOT)
    val kind = when {
        status == 401 -> ChatFailureKind.AUTH
        status == 403 && listOf("unsupported_country_region_territory", "unsupported_region", "region_not_supported")
            .any { low.contains(it) } -> ChatFailureKind.REGION
        status == 403 -> ChatFailureKind.FORBIDDEN
        status == 402 || status == 429 && listOf("insufficient_quota", "quota_exceeded", "billing_hard_limit_reached")
            .any { low.contains(it) } -> ChatFailureKind.CREDITS
        status == 429 -> ChatFailureKind.RATE_LIMIT
        status == 404 -> ChatFailureKind.MODEL
        status == 408 || status == 504 || low.contains("sockettimeoutexception") ||
            low.contains("timed out") || low.contains("timeout") -> ChatFailureKind.TIMEOUT
        status != null && status in 500..599 -> ChatFailureKind.SERVER
        low.contains("sslhandshakeexception") || low.contains("certpathvalidatorexception") ->
            ChatFailureKind.TLS
        low.contains("unknownhostexception") || low.contains("connectexception") ||
            low.contains("socketexception") || low.contains("network is unreachable") ||
            low.contains("failed to connect") -> ChatFailureKind.NETWORK
        else -> ChatFailureKind.OTHER
    }
    return ChatFailureGuide(kind, status)
}
