package com.androidharness.app.plugins

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.Assume.assumeTrue
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class AiRuntimeBridgeTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun quotedPromptRunsAsExactlyOneArgumentAndNeverExecutesShellPayload() {
        assumeTrue(File("/bin/sh").canExecute())
        val bin = temp.newFolder("bin")
        val fake = File(bin, "codex")
        fake.writeText("""
            #!/bin/sh
            printf '%s\n' "$#" "$1" "$2" "$3" "$4"
        """.trimIndent() + "\n")
        assertTrue(fake.setExecutable(true))
        val marker = File(temp.root, "must-not-create")
        val attack = "hello'; \$(touch " + marker.absolutePath + "); echo 'bye"
        val command = AiRuntimeBridge.executionCommand("codex", attack, true)
        val shell = ProcessBuilder("sh", "-c", command)
            .directory(temp.root)
            .redirectErrorStream(true)
        shell.environment()["PATH"] = bin.absolutePath + ":" + System.getenv("PATH").orEmpty()
        val process = shell.start()
        assertTrue(process.waitFor(6, TimeUnit.SECONDS))
        val received = process.inputStream.bufferedReader().readLines()
        assertEquals(0, process.exitValue())
        assertEquals(listOf("4", "exec", "--sandbox", "workspace-write", "User request: " + attack), received)
        assertFalse("Shell metacharacters must stay inside the prompt argument", marker.exists())
    }

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
        assertTrue(AiRuntimeBridge.executionCommand("gemini", "read files", false).contains("--approval-mode plan"))
        assertTrue(AiRuntimeBridge.executionCommand("aider", "read files", false).contains("--dry-run --message"))
        for (name in listOf("opencode", "pi", "qwen")) {
            assertThrows(IllegalArgumentException::class.java) {
                AiRuntimeBridge.executionCommand(name, "hello", false)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("unknown", "task", true)
        }
    }

    @Test fun resumesOnlyValidatedClaudeSessionWithSamePermissionMode() {
        val session = "123e4567-e89b-12d3-a456-426614174000"
        assertTrue(AiRuntimeBridge.isValidSessionId(session))
        val read = AiRuntimeBridge.executionCommand("claude", "review only", false, session)
        assertEquals("claude -p --permission-mode plan --resume '$session' 'User request: review only'", read)
        assertTrue(AiRuntimeBridge.executionCommand("claude", "fix it", true, session)
            .contains("--permission-mode acceptEdits --resume '$session'"))
        assertFalse(AiRuntimeBridge.isValidSessionId("x; touch /tmp/evil"))
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("claude", "review", false, "$session; touch /tmp/evil")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("codex", "review", false, session)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("claude", "review", false, "")
        }
        assertEquals("claude -p --permission-mode plan 'User request: review only'",
            AiRuntimeBridge.executionCommand("claude", "review only", false))
    }

    @Test fun resumesStrictlyValidatedOpenCodeAndGeminiSessions() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val opencode = "ses_4f2a1c7890"
        assertTrue(AiRuntimeBridge.isValidSessionForAgent("opencode", opencode))
        assertTrue(AiRuntimeBridge.isValidSessionForAgent("gemini", uuid))
        assertFalse(AiRuntimeBridge.isValidSessionForAgent("opencode", uuid))
        assertFalse(AiRuntimeBridge.isValidSessionForAgent("gemini", opencode))
        assertEquals("opencode run --session '$opencode' 'User request: Follow up'",
            AiRuntimeBridge.executionCommand("opencode", "Follow up", true, opencode))
        assertEquals("gemini --resume '$uuid' --approval-mode plan -p 'User request: Audit'",
            AiRuntimeBridge.executionCommand("gemini", "Audit", false, uuid))
        assertEquals("gemini --resume '$uuid' -p 'User request: Fix'",
            AiRuntimeBridge.executionCommand("gemini", "Fix", true, uuid))
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("opencode", "Read", true, "ses_abc; touch /tmp/evil")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("gemini", "Read", false, "latest")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("aider", "Read", true, uuid)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiRuntimeBridge.executionCommand("opencode", "Read", false, opencode)
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
