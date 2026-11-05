package com.androidharness.app.ui.chat

import org.junit.Assert.*
import org.junit.Test

class ChatFailureGuideTest {
    @Test fun authPermissionBillingAndRateAreDifferent() {
        assertEquals(ChatFailureKind.AUTH, classifyChatFailure("HTTP 401: invalid key").kind)
        assertEquals(ChatFailureKind.FORBIDDEN, classifyChatFailure("HTTP 403: denied").kind)
        assertEquals(ChatFailureKind.REGION, classifyChatFailure("HTTP 403: unsupported_region").kind)
        assertEquals(ChatFailureKind.CREDITS, classifyChatFailure("HTTP 402: payment required").kind)
        assertEquals(ChatFailureKind.CREDITS, classifyChatFailure("HTTP 429: insufficient_quota").kind)
        assertEquals(ChatFailureKind.RATE_LIMIT, classifyChatFailure("HTTP 429: slow down").kind)
        assertEquals(ChatFailureKind.MODEL, classifyChatFailure("HTTP 404: model missing").kind)
    }

    @Test fun networkTimeoutAndServerAreActionableWithoutGuessing() {
        assertEquals(ChatFailureKind.TIMEOUT, classifyChatFailure("java.net.SocketTimeoutException: timed out").kind)
        assertEquals(ChatFailureKind.NETWORK, classifyChatFailure("java.net.UnknownHostException: host").kind)
        assertEquals(ChatFailureKind.TLS, classifyChatFailure("javax.net.ssl.SSLHandshakeException: error").kind)
        assertEquals(ChatFailureKind.SERVER, classifyChatFailure("HTTP 503: overloaded").kind)
        assertEquals(ChatFailureKind.TIMEOUT, classifyChatFailure("HTTP 504: gateway").kind)
    }

    @Test fun untrustedNumbersAndBare403DoNotBecomeStatusOrRegion() {
        assertNull(classifyChatFailure("403 account 1234 429").httpStatus)
        assertEquals(ChatFailureKind.OTHER, classifyChatFailure("403 unsupported country").kind)
        assertNull(classifyChatFailure("https://test.invalid/401/token=abcd").httpStatus)
        assertEquals(ChatFailureKind.FORBIDDEN, classifyChatFailure("HTTP 403: region").kind)
    }

    @Test fun exportedReportNeverCopiesSecretOrRawProviderErrors() {
        val secret = "sk-private-very-sensitive"
        val url = "https://api.example.test:443/model?access_token=" + secret
        val failure = classifyChatFailure("HTTP 401: " + url + " password=" + secret)
        val report = failure.safeReport("v1;Bearer " + secret, ChatNetworkState.OFFLINE)
        assertTrue(report.contains("Failure: AUTH"))
        assertTrue(report.contains("HTTP: 401"))
        assertTrue(report.contains("Network: OFFLINE"))
        assertFalse(report.contains(secret))
        assertFalse(report.contains(url))
        assertFalse(report.contains("password"))
        assertFalse(report.contains("Bearer"))
    }
}
