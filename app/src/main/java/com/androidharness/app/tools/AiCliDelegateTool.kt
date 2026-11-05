package com.androidharness.app.tools

import com.androidharness.app.plugins.AiRuntimeBridge
import com.androidharness.app.github.gitCommand
import com.androidharness.app.data.env.ShellRunResult
import com.androidharness.app.data.env.ShellTierRouter
import com.androidharness.app.workspace.SshFs
import com.androidharness.app.workspace.WorkspaceFs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Delegate an explicit coding task to an independently installed AI CLI.
 *
 * Always classified as a modifying tool so the existing agent approval system
 * can gate it. Uses the same ShellPolicy denylist and selected shell transport
 * as regular coding tools. Does not install code or handle external credentials.
 */
class AiCliDelegateTool(
    private val run: suspend (String, WorkspaceFs, Int, Int) -> ShellRunResult,
) : Tool {
    constructor(router: ShellTierRouter) : this({ cmd, workspace, timeout, maxOutput ->
        router.runWorkspace(cmd, workspace, timeout, maxOutput)
    })

    override val name: String = "ai_cli_delegate"
    override val description: String =
        "Delegate one coding task to a user-installed and authenticated AI CLI in the current workspace. " +
        "Supported agents: codex, claude, opencode, gemini, pi, qwen, aider. " +
        "The CLI may call external AI services and use project files. Never installs a CLI or obtains API keys. " +
        "allow_edits=false uses read-only/plan/dry-run modes for codex, claude, gemini and aider. " +
        "Optional session_id resumes a pre-existing Claude/Gemini UUID or OpenCode ses_ session; no accounts, sessions or credentials are created. " +
        "This is a modifying tool requiring user approval."

    override val parametersSchema: JsonObject = Schema.obj(
        mapOf(
            "agent" to Schema.string("One of codex, claude, opencode, gemini, pi, qwen, aider."),
            "prompt" to Schema.string("Explicit coding task (1-2400 characters). Do not include secrets."),
            "allow_edits" to Schema.boolean("False requests read-only mode for codex/claude; true explicitly permits edits."),
            "session_id" to Schema.string("Optional existing Claude/Gemini UUID or OpenCode ses_ session ID in this workspace. Other agents cannot resume."),
        ),
        required = listOf("agent", "prompt"),
    )
    override val isReadOnly: Boolean = false

    override fun isAvailable(ctx: ToolContext): Boolean =
        ctx.workspace.shellRoot != null || ctx.workspace is SshFs

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        if (!isAvailable(ctx)) return ToolResult(false, "Select a workspace with an executable shell first.")
        val id = args["agent"]?.jsonPrimitive?.content ?: return ToolResult(false, "Missing agent.")
        val prompt = args["prompt"]?.jsonPrimitive?.content ?: return ToolResult(false, "Missing task.")
        val allowEdits = args["allow_edits"]?.jsonPrimitive?.booleanOrNull ?: false
        val sessionId = args["session_id"]?.jsonPrimitive?.content
        val cmd = try { AiRuntimeBridge.executionCommand(id, prompt, allowEdits, sessionId) }
            catch (e: IllegalArgumentException) { return ToolResult(false, e.message.orEmpty()) }
        val root = (ctx.workspace as? SshFs)?.root?.let(::File) ?: ctx.workspace.shellRoot
            ?: return ToolResult(false, "Selected workspace does not support shell execution.")
        val restriction = if (ctx.sandboxOff) null else ShellPolicy.denyReason(cmd, root, root)
        if (restriction != null) return ToolResult(false, restriction)

        val probe = run(AiRuntimeBridge.checkCommand(id), ctx.workspace, 12_000, 1_024)
        if (probe.timedOut || probe.exitCode != 0)
            return ToolResult(false, "The CLI $id is not installed or accessible in this workspace shell.")
        val result = run(cmd, ctx.workspace, 180_000, 24_000)
        val output = listOf(result.rawOutput, result.rawStderr).filter { it.isNotBlank() }
            .joinToString("\n").takeLast(24_000)
        // The agent can modify many files. Show the post-execution Git state as a
        // read-only review, without staging, committing, reverting or publishing.
        // This reflects the whole workspace, including pre-existing changes.
        val review = if (allowEdits) runCatching {
            run(
                gitCommand(listOf(
                    "-C", root.absolutePath,
                    "-c", "core.fsmonitor=false",
                    "-c", "core.hooksPath=/dev/null",
                    "status", "--short", "--untracked-files=normal"
                )),
                ctx.workspace, 15_000, 8_000
            )
        }.getOrNull() else null
        return ToolResult(
            ok = result.exitCode == 0 && !result.timedOut,
            output = SecretRedactor.redact(buildString {
                append("Agent: ").append(id)
                append("\nExit: ").append(if (result.timedOut) "timeout" else result.exitCode)
                if (result.note != null) append("\n").append(result.note)
                append("\n").append(if (output.isBlank()) "(No output)" else output)
                if (allowEdits) {
                    append("\n\nGit workspace review (includes pre-existing changes):")
                    if (review == null || review.timedOut || review.exitCode != 0) {
                        append("\nUnavailable (the workspace may not be a Git repository).")
                    } else {
                        append("\n").append(
                            review.rawOutput.takeLast(8_000).ifBlank { "Clean working tree." }
                        )
                    }
                    append("\nNo files were automatically staged, committed or pushed.")
                }
            }),
        )
    }
}
