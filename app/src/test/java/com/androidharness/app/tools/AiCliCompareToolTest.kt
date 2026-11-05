package com.androidharness.app.tools

import com.androidharness.app.data.env.ExecutionTier
import com.androidharness.app.data.env.ShellRunResult
import com.androidharness.app.workspace.FileFs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiCliCompareToolTest {
    @get:Rule val temp = TemporaryFolder()
    private val ctx get() = ToolContext(FileFs(temp.root))
    private fun output(code: Int = 0, text: String = "") =
        ShellRunResult(code, false, text, "", ExecutionTier.APP_LINUX, null)
    private fun args(agents: List<String>, prompt: String = "Review this change") = buildJsonObject {
        put("agents", buildJsonArray { agents.forEach { add(it) } })
        put("prompt", prompt)
    }

    @Test fun rejectsUnknownDuplicateOrUnsafeAgentsBeforeShell() = runBlocking {
        val called = mutableListOf<String>()
        val tool = AiCliCompareTool { cmd, _, _, _ -> called += cmd; output() }
        assertFalse(tool.isReadOnly)
        listOf(
            listOf("codex"), listOf("codex", "codex"), listOf("codex", "pi"),
            listOf("codex", "opencode"), listOf("codex", "claude; rm -rf /"),
            listOf("codex", "claude", "gemini", "aider", "another")
        ).forEach { assertFalse(tool.execute(args(it), ctx).ok) }
        assertFalse(tool.execute(args(listOf("codex", "claude"), ""), ctx).ok)
        assertTrue(called.isEmpty())
    }

    @Test fun executesTwoIsolatedReadOnlyModesAndReturnsLabeledAnswers() = runBlocking {
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        val tool = AiCliCompareTool { cmd, _, _, _ ->
            seen += cmd
            when {
                cmd.contains("codex exec") -> output(text = "Codex reviewed")
                cmd.contains("claude -p") -> output(text = "Claude reviewed")
                else -> output()
            }
        }
        val result = tool.execute(args(listOf("codex", "claude")), ctx)
        assertTrue(result.ok)
        assertTrue(result.output.indexOf("=== codex ===") < result.output.indexOf("=== claude ==="))
        assertTrue(result.output.contains("Codex reviewed"))
        assertTrue(result.output.contains("Claude reviewed"))
        assertTrue(seen.any { it.contains("codex exec --sandbox read-only") })
        assertTrue(seen.any { it.contains("claude -p --permission-mode plan") })
        assertTrue(seen.none { it.contains("workspace-write") || it.contains("acceptEdits") })
    }

    @Test fun missingAgentDoesNotDiscardHealthyAgentOutput() = runBlocking {
        val tool = AiCliCompareTool { cmd, _, _, _ ->
            if (cmd == "command -v gemini >/dev/null 2>&1") output(code = 127)
            else if (cmd.contains("aider --dry-run")) output(text = "Aider answer")
            else output()
        }
        val result = tool.execute(args(listOf("gemini", "aider")), ctx)
        assertTrue(result.ok)
        assertTrue(result.output.contains("NOT INSTALLED: gemini"))
        assertTrue(result.output.contains("SUCCESS\nAider answer"))
        assertTrue(result.output.contains("1/2 agents succeeded"))
    }

    @Test fun quotedMaliciousPromptRemainsOneArgumentInEachExecution() = runBlocking {
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        val tool = AiCliCompareTool { cmd, _, _, _ -> seen += cmd; output(text = "ok") }
        val dangerous = "John's code; \$(touch /tmp/not-a-real-file) `id`"
        assertTrue(tool.execute(args(listOf("codex", "aider"), dangerous), ctx).ok)
        val invocations = seen.filter { it.contains("User request:") }
        assertEquals(2, invocations.size)
        assertTrue(invocations.all { it.contains("John'\\''s") })
        assertTrue(invocations.any { it.contains("aider --dry-run --message") })
    }

    @Test fun compareRedactsSecretsFromAllAgentOutputsAndPreservesCommitSha() = runBlocking {
        val key = "gsk_" + "Aa0".repeat(20)
        val assignedSecret = "Bb1".repeat(20)
        val sha = "0123456789abcdef".repeat(4)
        val tool = AiCliCompareTool { cmd, _, _, _ ->
            when {
                cmd.contains("codex exec") -> ShellRunResult(
                    0, false, "GROQ_API_KEY=$key\ncommit $sha",
                    "AWS_SECRET_ACCESS_KEY=$assignedSecret", ExecutionTier.APP_LINUX, null
                )
                cmd.contains("claude -p") -> output(text = "Claude checked")
                else -> output()
            }
        }
        val result = tool.execute(args(listOf("codex", "claude")), ctx)
        assertTrue(result.ok)
        assertFalse(result.output.contains(key))
        assertFalse(result.output.contains(assignedSecret))
        assertTrue(result.output.contains("GROQ_API_KEY=[redacted]"))
        assertTrue(result.output.contains("AWS_SECRET_ACCESS_KEY=[redacted]"))
        assertTrue(result.output.contains("commit $sha"))
        assertTrue(result.output.contains("Claude checked"))
    }
}
