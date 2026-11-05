package com.androidharness.app.tools

import com.androidharness.app.data.env.ShellRunResult
import com.androidharness.app.data.env.ShellTierRouter
import com.androidharness.app.plugins.AiRuntimeBridge
import com.androidharness.app.workspace.SshFs
import com.androidharness.app.workspace.WorkspaceFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Compare one task across 2-4 independently installed coding agents.
 *
 * Only CLIs with an explicit read-only / plan / dry-run mode are supported;
 * responses remain separate so no agent is given another agent's output.
 * This tool is never exposed as automatically read-only, because launching
 * external agents may contact cloud services or execute tool calls.
 */
class AiCliCompareTool(
    private val run: suspend (String, WorkspaceFs, Int, Int) -> ShellRunResult,
) : Tool {
    constructor(router: ShellTierRouter) : this({ cmd, workspace, timeout, maxOutput ->
        router.runWorkspace(cmd, workspace, timeout, maxOutput)
    })

    override val name: String = "ai_cli_compare"
    override val description: String =
        "Ask 2-4 independently installed, authenticated coding agents the same question " +
        "in read-only/plan/dry-run mode, and compare their separate answers. " +
        "Choose only codex, claude, gemini, aider. This can use external AI credits " +
        "and requires tool approval; it does not install or authenticate agents."

    override val parametersSchema: JsonObject = Schema.obj(
        mapOf(
            "agents" to buildJsonObject {
                put("type", "array")
                put("description", "2-4 distinct CLI IDs: codex, claude, gemini, aider.")
                put("items", buildJsonObject { put("type", "string") })
                put("minItems", 2)
                put("maxItems", 4)
            },
            "prompt" to Schema.string("One non-secret coding question to compare (1-2400 characters)."),
        ),
        required = listOf("agents", "prompt"),
    )

    // External agent processes and API requests must require user/tool approval.
    override val isReadOnly: Boolean = false

    override fun isAvailable(ctx: ToolContext): Boolean =
        ctx.workspace.shellRoot != null || ctx.workspace is SshFs

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        if (!isAvailable(ctx)) return ToolResult(false, "Choose a workspace with shell access.")
        val requested = args["agents"] as? JsonArray
            ?: return ToolResult(false, "Choose 2-4 read-only agents.")
        val selected = try { requested.map { it.jsonPrimitive.content } }
            catch (_: IllegalArgumentException) { return ToolResult(false, "Agent names must be strings.") }
        if (selected.size !in 2..4 || selected.distinct().size != selected.size)
            return ToolResult(false, "Choose 2-4 different agents.")
        val allowed = AiRuntimeBridge.profiles.filter { it.supportsReadOnly }.map { it.id }.toSet()
        if (selected.any { it !in allowed })
            return ToolResult(false, "Only codex, claude, gemini, aider support reviewed comparison modes.")
        val prompt = args["prompt"]?.jsonPrimitive?.content
            ?: return ToolResult(false, "Missing comparison task.")
        val commands = try { selected.associateWith { AiRuntimeBridge.executionCommand(it, prompt, false) } }
            catch (e: IllegalArgumentException) { return ToolResult(false, e.message.orEmpty()) }

        val root = (ctx.workspace as? SshFs)?.root?.let(::File) ?: ctx.workspace.shellRoot
            ?: return ToolResult(false, "Workspace cannot run shell commands.")
        if (!ctx.sandboxOff) {
            for (command in commands.values) {
                val reason = ShellPolicy.denyReason(command, root, root)
                if (reason != null) return ToolResult(false, reason)
            }
        }

        // Two processes at most avoid saturating a phone and minimize account/rate-limit bursts.
        val slots = Semaphore(2)
        return coroutineScope {
            val checksAndRuns = selected.map { id ->
                async {
                    slots.withPermit {
                        try {
                            val probe = run(AiRuntimeBridge.checkCommand(id), ctx.workspace, 12_000, 512)
                            if (probe.exitCode != 0 || probe.timedOut) {
                                return@withPermit id to "NOT INSTALLED: $id is unavailable in this workspace."
                            }
                            val response = run(commands.getValue(id), ctx.workspace, 120_000, 8_000)
                            val result = listOf(response.rawOutput, response.rawStderr)
                                .filter(String::isNotBlank).joinToString("\n").takeLast(8_000)
                            val status = if (response.timedOut) "TIMEOUT" else if (response.exitCode == 0) "SUCCESS" else "EXIT ${response.exitCode}"
                            id to "$status\n${result.ifBlank { "(No output)" }}"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            id to "ERROR: ${e.localizedMessage?.take(200) ?: "Unknown execution error"}"
                        }
                    }
                }
            }
            // Keep answers in selection order even if a faster agent finishes first.
            val results = checksAndRuns.map { it.await() }
            val successes = results.count { (_, value) -> value.startsWith("SUCCESS\n") }
            ToolResult(
                ok = successes > 0,
                output = SecretRedactor.redact(buildString {
                    append("Read-only AI comparison: $successes/${results.size} agents succeeded.")
                    for ((id, response) in results) {
                        append("\n\n=== ").append(id).append(" ===\n")
                        append(response)
                    }
                    append("\n\nCompare the independent answers and verify important claims.")
                }),
            )
        }
    }
}
