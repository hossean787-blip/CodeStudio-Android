package com.androidharness.app.tools

import com.androidharness.app.data.env.ExecutionTier
import com.androidharness.app.data.env.ShellRunResult
import com.androidharness.app.workspace.FileFs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiCliDelegateToolTest {
    @get:Rule val temp = TemporaryFolder()
    private fun result(exit: Int = 0, stdout: String = "") =
        ShellRunResult(exit, false, stdout, "", ExecutionTier.APP_LINUX, null)

    @Test fun toolRequiresApprovalAndNeverRunsAnUnknownAgent() = runBlocking {
        val invoked = mutableListOf<String>()
        val tool = AiCliDelegateTool { cmd, _, _, _ ->
            invoked += cmd
            result()
        }
        val ctx = ToolContext(FileFs(temp.root))
        assertFalse(tool.isReadOnly)
        assertTrue(tool.isAvailable(ctx))
        val invalid = tool.execute(buildJsonObject {
            put("agent", "codex; pwd"); put("prompt", "task"); put("allow_edits", true)
        }, ctx)
        assertFalse(invalid.ok)
        assertTrue(invoked.isEmpty())
    }

    @Test fun detectsAvailableExecutableThenRunsWithReadOnlySandbox() = runBlocking {
        val commands = mutableListOf<String>()
        val tool = AiCliDelegateTool { cmd, _, _, _ ->
            commands += cmd
            result(stdout = if (commands.size == 2) "Completed review" else "")
        }
        val outcome = tool.execute(buildJsonObject {
            put("agent", "codex"); put("prompt", "Review changes"); put("allow_edits", false)
        }, ToolContext(FileFs(temp.root)))
        assertTrue(outcome.ok)
        assertTrue(outcome.output.contains("Completed review"))
        assertEquals(2, commands.size)
        assertEquals("command -v codex >/dev/null 2>&1", commands[0])
        assertTrue(commands[1].contains("--sandbox read-only"))
    }

    @Test fun refusesUnavailableCliAndNoUnapprovedWriteMode() = runBlocking {
        val commands = mutableListOf<String>()
        val tool = AiCliDelegateTool { cmd, _, _, _ ->
            commands += cmd
            result(exit = 127)
        }
        val ctx = ToolContext(FileFs(temp.root))
        val absent = tool.execute(buildJsonObject {
            put("agent", "pi"); put("prompt", "Review"); put("allow_edits", true)
        }, ctx)
        assertFalse(absent.ok)
        assertEquals(1, commands.size)
        commands.clear()
        val denied = tool.execute(buildJsonObject {
            put("agent", "pi"); put("prompt", "Review"); put("allow_edits", false)
        }, ctx)
        assertFalse(denied.ok)
        assertTrue(commands.isEmpty())
    }
}
