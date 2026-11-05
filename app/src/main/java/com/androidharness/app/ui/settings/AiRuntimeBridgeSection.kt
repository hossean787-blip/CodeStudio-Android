package com.androidharness.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.androidharness.app.AppContainer
import com.androidharness.app.R
import com.androidharness.app.plugins.AiRuntimeBridge
import com.androidharness.app.tools.AiCliCompareTool
import com.androidharness.app.tools.ToolContext
import com.androidharness.app.tools.SecretRedactor
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import com.androidharness.app.tools.ShellPolicy
import com.androidharness.app.workspace.SshFs
import java.io.File
import com.androidharness.app.workspace.WorkspaceFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * An opt-in bridge to independently installed coding CLIs.
 * It never downloads executable packages or uses credentials itself.
 *
 * The command is executed via the currently selected project's existing
 * shell policy. Outputs remain in this view and are not saved or uploaded.
 */
@Composable
internal fun AiRuntimeBridgeSection(container: AppContainer, workspace: WorkspaceFs?) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var executing by remember { mutableStateOf(false) }
    var availability by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var selectedId by rememberSaveable { mutableStateOf("codex") }
    var comparisonAgents by remember { mutableStateOf(setOf("codex", "claude")) }
    var comparing by remember { mutableStateOf(false) }
    var compareConfirmation by remember { mutableStateOf(false) }
    var compareResult by remember { mutableStateOf<String?>(null) }

    var prompt by rememberSaveable { mutableStateOf("") }
    // A session UUID is ephemeral and must not leak between workspaces.
    var resumeSessionId by remember { mutableStateOf("") }
    var allowEdits by rememberSaveable { mutableStateOf(false) }
    var awaitingConfirmation by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    // PATH checks belong to a specific workspace; never reuse availability from another one.
    LaunchedEffect(workspace?.displayPath, selectedId) {
        resumeSessionId = ""
        availability = emptyMap()
        result = null
        failure = null
    }
    val selected = AiRuntimeBridge.profiles.single { it.id == selectedId }
    val canCompare = (workspace?.shellRoot != null || workspace is SshFs) &&
        !executing && !checking && !comparing && prompt.isNotBlank() &&
        comparisonAgents.size in 2..4 && comparisonAgents.all { availability[it] == true }
    val canRun = (workspace?.shellRoot != null || workspace is SshFs) && !executing && !checking &&
        availability[selectedId] == true && prompt.isNotBlank() &&
        (selected.supportsReadOnly || allowEdits) &&
        (resumeSessionId.isBlank() || AiRuntimeBridge.isValidSessionForAgent(selectedId, resumeSessionId))

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = androidx.compose.ui.res.stringResource(R.string.ai_runtime_title),
                style = MaterialTheme.typography.titleLarge)
            Text(text = androidx.compose.ui.res.stringResource(R.string.ai_runtime_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (workspace == null) {
                Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_no_workspace),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = {
                val captured = workspace?.takeIf { it.shellRoot != null || it is SshFs } ?: return@OutlinedButton
                checking = true
                failure = null
                scope.launch {
                    try {
                        val checked = mutableMapOf<String, Boolean>()
                        for (profile in AiRuntimeBridge.profiles) {
                            val response = container.shellRouter.runWorkspace(
                                AiRuntimeBridge.checkCommand(profile.id), captured, 12_000, 500)
                            checked[profile.id] = !response.timedOut && response.exitCode == 0
                            availability = checked.toMap()
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failure = SecretRedactor.redact(e.localizedMessage?.take(400) ?: "Check failed.") }
                    finally { checking = false }
                }
            }, enabled = (workspace?.shellRoot != null || workspace is SshFs) && !checking && !executing,
                modifier = Modifier.fillMaxWidth()) {
                Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_check))
            }
            if (checking || executing) LinearProgressIndicator(Modifier.fillMaxWidth())
            AiRuntimeBridge.profiles.forEach { backend ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedId == backend.id,
                        onClick = { if (!executing && !checking) selectedId = backend.id },
                        enabled = !executing && !checking)
                    Text(backend.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    val ready = availability[backend.id]
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            if (ready == true) R.string.ai_runtime_ready
                            else if (ready == false) R.string.ai_runtime_missing
                            else R.string.ai_runtime_unchecked
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ready == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it.take(2400) },
                label = { Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_prompt)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                enabled = !executing,
            )
            if (selectedId in setOf("claude", "opencode", "gemini")) {
                OutlinedTextField(
                    value = resumeSessionId,
                    onValueChange = { resumeSessionId = it.take(64) },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_resume_session)) },
                    supportingText = { Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_resume_warning)) },
                    isError = resumeSessionId.isNotBlank() && !AiRuntimeBridge.isValidSessionForAgent(selectedId, resumeSessionId),
                    singleLine = true,
                    enabled = !executing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allowEdits, onCheckedChange = { allowEdits = it }, enabled = !executing)
                Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_edit_ack),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f))
            }
            Button(
                onClick = { awaitingConfirmation = true },
                enabled = canRun,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_run))
            }
            HorizontalDivider()
            Text(androidx.compose.ui.res.stringResource(R.string.ai_compare_title),
                style = MaterialTheme.typography.titleMedium)
            Text(androidx.compose.ui.res.stringResource(R.string.ai_compare_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AiRuntimeBridge.profiles.filter { it.supportsReadOnly }.forEach { agent ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = agent.id in comparisonAgents,
                        onCheckedChange = { checked ->
                            comparisonAgents = if (checked) comparisonAgents + agent.id
                                else comparisonAgents - agent.id
                        },
                        enabled = !comparing && !executing && availability[agent.id] == true,
                    )
                    Text(agent.label)
                }
            }
            Button(
                onClick = { compareConfirmation = true },
                enabled = canCompare,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.ai_compare_run))
            }
            if (comparing) LinearProgressIndicator(Modifier.fillMaxWidth())
            compareResult?.let {
                Text(it, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
            failure?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            result?.let { output ->
                Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_output),
                    style = MaterialTheme.typography.titleSmall)
                Text(output, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (compareConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!comparing) compareConfirmation = false },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.ai_compare_confirm_title)) },
            text = { Text(androidx.compose.ui.res.stringResource(R.string.ai_compare_confirm_body,
                comparisonAgents.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val captured = workspace ?: return@TextButton
                    val chosen = AiRuntimeBridge.profiles.map { it.id }
                        .filter { it in comparisonAgents }
                    compareConfirmation = false
                    comparing = true
                    compareResult = null
                    scope.launch {
                        try {
                            val args = buildJsonObject {
                                put("agents", buildJsonArray { chosen.forEach { add(it) } })
                                put("prompt", prompt)
                            }
                            val output = AiCliCompareTool(container.shellRouter)
                                .execute(args, ToolContext(captured))
                            compareResult = output.output
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { compareResult = SecretRedactor.redact(e.localizedMessage?.take(400) ?: "Comparison failed.") }
                        finally { comparing = false }
                    }
                }, enabled = !comparing) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ai_compare_run))
                }
            },
            dismissButton = {
                TextButton(onClick = { compareConfirmation = false }, enabled = !comparing) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
                }
            }
        )
    }
    if (awaitingConfirmation) {
        AlertDialog(onDismissRequest = { if (!executing) awaitingConfirmation = false },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_confirm_title)) },
            text = { Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_confirm_body,
                selected.label)) },
            confirmButton = {
                TextButton(onClick = {
                    val captured = workspace ?: return@TextButton
                    val command = try {
                        AiRuntimeBridge.executionCommand(selectedId, prompt, allowEdits,
                            resumeSessionId.takeIf { it.isNotBlank() && selectedId in setOf("claude", "opencode", "gemini") })
                    } catch (e: IllegalArgumentException) {
                        failure = e.message
                        awaitingConfirmation = false
                        return@TextButton
                    }
                    // Apply the same shell command policy as the coding agent's tool.
                    val root = (captured as? SshFs)?.root?.let(::File) ?: captured.shellRoot
                    val restriction = root?.let { ShellPolicy.denyReason(command, it, it) }
                    if (root == null || restriction != null) {
                        failure = restriction ?: "This workspace has no executable shell."
                        awaitingConfirmation = false
                        return@TextButton
                    }
                    awaitingConfirmation = false
                    executing = true
                    result = null
                    failure = null
                    scope.launch {
                        try {
                            val response = container.shellRouter.runWorkspace(command, captured, 180_000, 24_000)
                            val text = listOf(response.rawOutput, response.rawStderr).filter(String::isNotBlank)
                                .joinToString("\n").takeLast(24_000)
                            result = SecretRedactor.redact(if (text.isNotBlank()) text else "(No output)")
                            if (response.timedOut) failure = "Command timed out. Verify the CLI process before retrying."
                            else if (response.exitCode != 0) failure = "CLI returned exit code ${response.exitCode}."
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { failure = SecretRedactor.redact(e.localizedMessage?.take(400) ?: "Execution failed.") }
                        finally { executing = false }
                    }
                }, enabled = !executing) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ai_runtime_run))
                }
            },
            dismissButton = {
                TextButton(onClick = { awaitingConfirmation = false }, enabled = !executing) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
