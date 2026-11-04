package com.androidharness.app.plugins

import org.junit.Assert.*
import org.junit.Test

class AiRuntimeBridgeTest {
    @Test fun profilesAreUniqueAndCheckOnlyFixedExecutables() {
        assertEquals(7, AiRuntimeBridge.profiles.size)
        assertEquals(7, AiRuntimeBridge.profiles.map { it.id }.distinct().size)
        AiRuntimeBridge.profiles.forEach {
            assertTrue(it.executable.matches(Regex("[a-z][a-z0-9-]{0,24}")))
            assertEquals("command -v ${it.executable} >/dev/null 2>&1", AiRuntimeBridge.checkCommand(it.id))
        }
        assertThrows(IllegalArgumentException::class.java) { AiRuntimeBridge.checkCommand("codex; echo owned") }
    }

    @Test fun shellPayloadCannotEscapeSingleArgument() {
        val dangerous = "edit John's project; \$(touch /tmp/SHOULD_NOT_EXIST) `id` --dangerously-skip-permissions"
        val command = AiRuntimeBridge.executionCommand("codex", dangerous, true)
        assertTrue(command.startsWith("codex exec --sandbox workspace-write 'User request: "))
        assertTrue(command.contains("John'\\''s"))
        assertTrue(command.endsWith("'"))
        assertFalse(command.contains(" --dangerously-skip-permissions --"))
        assertTrue(AiRuntimeBridge.quoteArgument("a'b").contains("'\\''"))
    }

    @Test fun readOnlyModeIsExplicitAndCannotRunUnknownCli() {
        assertTrue(AiRuntimeBridge.executionCommand("codex", "audit only", false).contains("--sandbox read-only"))
        assertTrue(AiRuntimeBridge.executionCommand("claude", "read files", false).contains("--permission-mode plan"))
        for (name in listOf("opencode", "gemini", "pi", "qwen", "aider")) {
            assertThrows(IllegalArgumentException::class.java) {
                AiRuntimeBridge.executionCommand(name, "hello", false)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("unknown", "task", true)
        }
    }

    @Test fun rejectEmptyOversizedAndControlCharacterPrompts() {
        assertThrows(IllegalArgumentException::class.java) { AiRuntimeBridge.executionCommand("codex", " ", false) }
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("codex", "a".repeat(2401), false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("codex", "bad\u0000prompt", false)
        }
        assertTrue(AiRuntimeBridge.executionCommand("codex", "-n", false).contains("'User request: -n'"))
    }
}
