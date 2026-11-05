package com.androidharness.app.ui.integrations

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.R
import com.androidharness.app.data.PendingShare
import com.androidharness.app.integrations.IntegrationCatalog
import com.androidharness.app.integrations.IntegrationEntry
import com.androidharness.app.integrations.IntegrationPath
import com.androidharness.app.ui.common.AppHeader

/** Connection directory. No ChatGPT-side connector credentials are inherited by this Android app. */
@Composable
fun ConnectedAppsScreen(
    container: AppContainer,
    focusId: String,
    onBack: () -> Unit,
    onGitHub: () -> Unit,
    onMcp: () -> Unit,
    onPlugins: () -> Unit,
) {
    val context = LocalContext.current
    val github by container.github.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                require(uri.scheme == "content") { "Only a document selected by the user can be imported." }
                // A user-selected document can be supplied by Drive's Android DocumentsProvider,
                // if present. This is NOT account-wide Google Drive OAuth access.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                container.pendingShare.value = PendingShare(
                    streams = listOf(uri),
                    mime = context.contentResolver.getType(uri),
                )
                error = null
            } catch (e: Exception) {
                error = context.getString(R.string.saba_integrations_file_error)
            }
        }
    }
    val visible = remember(focusId) { IntegrationCatalog.selectedFirst(focusId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppHeader(title = stringResource(R.string.saba_integrations_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(R.string.saba_integrations_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.saba_integrations_no_inheritance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp))
                }
            }
            items(visible, key = { it.id }) { entry ->
                OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center) {
                                Icon(integrationIcon(entry), null, Modifier.size(23.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold)
                                Text(
                                    when (entry.path) {
                                        IntegrationPath.GITHUB ->
                                            github.login?.takeIf { it.isNotBlank() }
                                                ?.let { context.getString(R.string.saba_integrations_connected_as, it) }
                                                ?: context.getString(R.string.saba_integrations_needs_account)
                                        IntegrationPath.EMAIL_COMPOSE -> stringResource(R.string.saba_integrations_gmail_scope)
                                        IntegrationPath.DOCUMENT_PICKER -> stringResource(R.string.saba_integrations_drive_scope)
                                        IntegrationPath.MCP_OR_PLUGIN -> stringResource(R.string.saba_integrations_not_connected)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        when (entry.path) {
                            IntegrationPath.GITHUB -> {
                                Button(onClick = onGitHub) {
                                    Text(stringResource(R.string.saba_integrations_github_action))
                                }
                            }
                            IntegrationPath.EMAIL_COMPOSE -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_SENDTO,
                                                Uri.parse("mailto:")))
                                            error = null
                                        } catch (_: Exception) {
                                            error = context.getString(R.string.saba_integrations_email_error)
                                        }
                                    }) { Text(stringResource(R.string.saba_integrations_compose_email)) }
                                    TextButton(onClick = onMcp) {
                                        Text(stringResource(R.string.saba_integrations_configure_mcp))
                                    }
                                }
                            }
                            IntegrationPath.DOCUMENT_PICKER -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = { documentPicker.launch(arrayOf("*/*")) }) {
                                        Text(stringResource(R.string.saba_integrations_pick_document))
                                    }
                                    TextButton(onClick = onMcp) {
                                        Text(stringResource(R.string.saba_integrations_configure_mcp))
                                    }
                                }
                            }
                            IntegrationPath.MCP_OR_PLUGIN -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = onMcp) {
                                        Text(stringResource(R.string.saba_integrations_configure_mcp))
                                    }
                                    TextButton(onClick = onPlugins) {
                                        Text(stringResource(R.string.saba_integrations_plugins))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun integrationIcon(entry: IntegrationEntry): ImageVector = when (entry.id) {
    "github" -> Icons.Outlined.Code
    "gmail" -> Icons.Outlined.Email
    "drive" -> Icons.Outlined.Cloud
    "calendar" -> Icons.Outlined.Event
    "contacts" -> Icons.Outlined.People
    "canva", "figma", "openart" -> Icons.Outlined.Palette
    "sharepoint" -> Icons.Outlined.Folder
    "linear" -> Icons.Outlined.ListAlt
    "supabase" -> Icons.Outlined.Storage
    "replit", "context7" -> Icons.Outlined.Terminal
    "heygen", "higgsfield" -> Icons.Outlined.Videocam
    "firecrawl" -> Icons.Outlined.Search
    "unusual-whales" -> Icons.Outlined.ShowChart
    "chatgpt-space" -> Icons.Outlined.Chat
    else -> Icons.Outlined.Extension
}
