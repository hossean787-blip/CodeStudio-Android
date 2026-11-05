package com.androidharness.app.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.androidharness.app.BuildConfig
import com.androidharness.app.R
import com.androidharness.app.ui.chat.ChatFailureKind
import com.androidharness.app.ui.chat.ChatNetworkState
import com.androidharness.app.ui.chat.classifyChatFailure
import com.androidharness.app.ui.common.TextButton

/** A real system connectivity check, not a provider availability or quota assertion. */
private fun networkState(manager: ConnectivityManager): ChatNetworkState = try {
    val active = manager.activeNetwork
    val caps = active?.let(manager::getNetworkCapabilities)
    when {
        active == null -> ChatNetworkState.OFFLINE
        caps == null -> ChatNetworkState.NOT_VALIDATED
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> ChatNetworkState.VALIDATED
        else -> ChatNetworkState.NOT_VALIDATED
    }
} catch (_: SecurityException) {
    ChatNetworkState.UNKNOWN
}

@Composable
private fun rememberChatNetworkState(): ChatNetworkState {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var state by remember(manager) { mutableStateOf(networkState(manager)) }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state = networkState(manager) }
            override fun onLost(network: Network) { state = networkState(manager) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                state = networkState(manager)
            }
        }
        val registered = try { manager.registerDefaultNetworkCallback(callback); true }
            catch (_: SecurityException) { false }
            catch (_: RuntimeException) { false }
        state = networkState(manager)
        onDispose { if (registered) runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return state
}

private fun ChatFailureKind.title(): Int = when (this) {
    ChatFailureKind.AUTH -> R.string.saba_error_auth
    ChatFailureKind.FORBIDDEN -> R.string.saba_chat_failure_forbidden
    ChatFailureKind.REGION -> R.string.saba_chat_failure_region
    ChatFailureKind.CREDITS -> R.string.saba_chat_failure_credits
    ChatFailureKind.RATE_LIMIT -> R.string.saba_error_rate
    ChatFailureKind.MODEL -> R.string.saba_error_model
    ChatFailureKind.TIMEOUT -> R.string.saba_error_timeout
    ChatFailureKind.SERVER -> R.string.saba_error_server
    ChatFailureKind.TLS -> R.string.saba_error_tls
    ChatFailureKind.NETWORK -> R.string.saba_error_network
    ChatFailureKind.OTHER -> R.string.saba_error_other
}
private fun ChatFailureKind.advice(): Int = when (this) {
    ChatFailureKind.AUTH -> R.string.saba_advice_auth
    ChatFailureKind.FORBIDDEN -> R.string.saba_advice_forbidden
    ChatFailureKind.REGION -> R.string.saba_advice_region
    ChatFailureKind.CREDITS -> R.string.saba_advice_credits
    ChatFailureKind.RATE_LIMIT -> R.string.saba_advice_rate
    ChatFailureKind.MODEL -> R.string.saba_advice_model
    ChatFailureKind.TIMEOUT -> R.string.saba_advice_timeout
    ChatFailureKind.SERVER -> R.string.saba_advice_server
    ChatFailureKind.TLS -> R.string.saba_advice_tls
    ChatFailureKind.NETWORK -> R.string.saba_advice_network
    ChatFailureKind.OTHER -> R.string.saba_advice_other
}

@Composable
internal fun ChatErrorRecoveryCard(
    failureMessage: String,
    canRetry: Boolean,
    canResume: Boolean,
    onRetry: () -> Unit,
    onResume: () -> Unit,
    onTroubleshoot: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val failure = remember(failureMessage) { classifyChatFailure(failureMessage) }
    val connection = rememberChatNetworkState()
    var copied by remember(failure, connection) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .25f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.saba_error_center_title),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(failure.kind.title()), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(failure.kind.advice()), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (failure.httpStatus != null) {
                Text(stringResource(R.string.saba_error_http, failure.httpStatus),
                    style = MaterialTheme.typography.labelSmall)
            }
            Text(stringResource(when (connection) {
                ChatNetworkState.VALIDATED -> R.string.saba_network_validated
                ChatNetworkState.OFFLINE -> R.string.saba_network_offline
                ChatNetworkState.NOT_VALIDATED -> R.string.saba_network_unverified
                ChatNetworkState.UNKNOWN -> R.string.saba_network_unknown
            }), style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (canRetry) TextButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.saba_error_retry))
                }
                if (canResume) TextButton(onClick = onResume) { Text(stringResource(R.string.saba_error_resume)) }
                TextButton(onClick = onTroubleshoot) { Text(stringResource(R.string.saba_error_settings)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    val report = failure.safeReport(BuildConfig.VERSION_NAME, connection)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.saba_error_center_title), report))
                    copied = true
                }) { Text(stringResource(if (copied) R.string.saba_error_copied else R.string.saba_error_copy)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.saba_error_close)) }
            }
        }
    }
}
