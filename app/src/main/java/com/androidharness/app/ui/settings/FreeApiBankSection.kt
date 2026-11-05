package com.androidharness.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.androidharness.app.R

/**
 * Provider discovery is informational until the user explicitly opens the
 * existing secure key/model sheet. No API request, signup or paid upgrade is automatic.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FreeApiBankSection(onConnect: (ProviderBrand) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyConnectable by rememberSaveable { mutableStateOf(false) }
    val links = LocalUriHandler.current

    OutlinedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.free_api_bank_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.free_api_bank_intro),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.free_api_bank_collapse else R.string.free_api_bank_expand))
            }
            if (expanded) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text(stringResource(R.string.free_api_bank_search)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(selected = onlyConnectable, onClick = { onlyConnectable = !onlyConnectable },
                    label = { Text(stringResource(R.string.free_api_bank_ready_filter)) })
                FreeApiCatalog.search(query, onlyConnectable).forEach { entry ->
                    val preset = entry.connectionPreset()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                        val tierLabel = when (entry.tier) {
                            FreeApiTier.LIMITED_FREE -> R.string.free_api_bank_tier_free
                            FreeApiTier.TRIAL -> R.string.free_api_bank_tier_trial
                            FreeApiTier.LOCAL_NO_KEY -> R.string.free_api_bank_tier_local
                            FreeApiTier.SETUP_REQUIRED -> R.string.free_api_bank_tier_setup
                            FreeApiTier.DIRECTORY -> R.string.free_api_bank_tier_directory
                        }
                        Text(stringResource(tierLabel), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (entry.noCardConfirmed) Text(stringResource(R.string.free_api_bank_no_card),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { links.openUri(entry.officialUrl) }) {
                                Text(stringResource(R.string.free_api_bank_official_docs))
                            }
                            if (preset != null) {
                                Button(onClick = { onConnect(preset) }) {
                                    Text(stringResource(R.string.free_api_bank_connect))
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.free_api_bank_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
