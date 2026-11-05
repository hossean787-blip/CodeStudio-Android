package com.androidharness.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.androidharness.app.R
import com.androidharness.app.llm.ModelsDev
import com.androidharness.app.llm.ProviderConfig
import com.androidharness.app.local.LocalModelCatalog
import java.util.Locale

/** "Accessible" is deliberately limited to a /models result, never chat or billing. */
internal enum class ProviderCatalogCheck { NOT_TESTED, CHECKING, CATALOG_ACCESSIBLE, FAILED }

@Composable
internal fun ProviderApiStatusSection(
    provider: ProviderConfig,
    status: ProviderCatalogCheck,
    onCheck: () -> Unit,
) {
    val local = LocalModelCatalog.isLocal(provider.id) ||
        provider.baseUrl.startsWith("http://127.0.0.1:") ||
        provider.baseUrl.startsWith("http://localhost:") ||
        provider.baseUrl.startsWith("http://[::1]:")
    val rate = ModelsDev.entry(ModelsDev.providerKeyFor(provider.baseUrl), provider.model)?.cost
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            when {
                local -> stringResource(R.string.saba_api_management_local_price)
                rate != null -> stringResource(
                    R.string.saba_api_management_price,
                    String.format(Locale.US, "%.4f", rate.input),
                    String.format(Locale.US, "%.4f", rate.output),
                )
                else -> stringResource(R.string.saba_api_management_price_unknown)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(when (status) {
                    ProviderCatalogCheck.NOT_TESTED -> R.string.saba_api_management_untested
                    ProviderCatalogCheck.CHECKING -> R.string.saba_api_management_testing
                    ProviderCatalogCheck.CATALOG_ACCESSIBLE -> R.string.saba_api_management_catalog_ok
                    ProviderCatalogCheck.FAILED -> R.string.saba_api_management_catalog_failed
                }),
                style = MaterialTheme.typography.labelSmall,
                color = if (status == ProviderCatalogCheck.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                modifier = Modifier.align(Alignment.End),
                enabled = status != ProviderCatalogCheck.CHECKING,
                onClick = onCheck,
            ) { Text(stringResource(R.string.saba_api_management_connection_test)) }
        }
    }
}
