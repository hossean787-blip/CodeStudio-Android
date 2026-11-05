package com.androidharness.app.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator smoke validates the APK adapter, not cloud authentication. */
@RunWith(AndroidJUnit4::class)
class AiRuntimeBridgeDeviceTest {
    @Test fun claudeSessionResumeIsValidatedOnAndroidWithoutLaunchingCli() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        assertTrue(AiRuntimeBridge.isValidSessionId(uuid))
        val command = AiRuntimeBridge.executionCommand("claude", "Read only", false, uuid)
        assertEquals("claude -p --permission-mode plan --resume '$uuid' 'User request: Read only'", command)
        try {
            AiRuntimeBridge.executionCommand("claude", "Read only", false, "bad; id")
            fail("Invalid session ID accepted")
        } catch (_: IllegalArgumentException) {
            // Reject invalid identifiers before any shell command is launched.
        }
    }
}
