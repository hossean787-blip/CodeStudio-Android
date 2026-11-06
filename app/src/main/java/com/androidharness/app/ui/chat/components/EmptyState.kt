package com.androidharness.app.ui.chat.components

import com.androidharness.app.ui.theme.HarnessGlassSurface as Surface
import com.androidharness.app.ui.theme.*

import androidx.compose.material3.OutlinedButton

import androidx.compose.foundation.layout.Arrangement

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.androidharness.app.R
import com.androidharness.app.ui.common.SabaBrandMark
import kotlinx.coroutines.delay
import com.androidharness.app.ui.common.Button

/**
 * First-run / new-chat screen.
 *
 * One mark, one headline, one supporting line, and a single quiet list of
 * suggestions with hairline separators, no hero squircles, color pills, or
 * status badges. Without a provider there is a real call-to-action button
 * instead of instructions to hunt through menus.
 */

/**
 * Saba Studio — chat-first entry surface.
 *
 * The same suggestion callbacks, agent chooser and provider onboarding are retained.
 * A minimal ChatGPT-like layout uses neon only as a subtle brand accent, rather than
 * making every element compete for attention. Project and terminal tools remain
 * accessible through the app's existing navigation.
 */
@Composable
internal fun EmptyState(
    hasProvider: Boolean,
    onSuggestion: (String) -> Unit,
    onAddProvider: () -> Unit,
    chatOnly: Boolean = false,
    agent: com.androidharness.app.data.ChatAgentProfile? = null,
    agentSetup: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val cyan = scheme.primary
    val violet = scheme.tertiary
    val ideas = if (chatOnly && agent != null && agent.starters.isNotEmpty()) {
        agent.starters.take(4).map { Icons.Outlined.Lightbulb to it }
    } else if (chatOnly) {
        listOf(
            Icons.Outlined.School to context.getString(R.string.chat_suggestion_explain),
            Icons.Outlined.Lightbulb to context.getString(R.string.chat_suggestion_ideas),
            Icons.Outlined.EditNote to context.getString(R.string.chat_suggestion_draft),
        )
    } else listOf(
        Icons.Outlined.Code to context.getString(R.string.saba_home_code_new_android),
        Icons.Outlined.Folder to context.getString(R.string.saba_home_code_review_source),
        Icons.Outlined.Terminal to context.getString(R.string.saba_home_code_python),
        Icons.Outlined.CreateNewFolder to context.getString(R.string.saba_home_code_readme),
    )

    Box(
        modifier = modifier.fillMaxWidth().fillMaxHeight()
            .background(Brush.verticalGradient(listOf(scheme.surface,
                scheme.primaryContainer.copy(alpha = 0.19f), scheme.surface))),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (chatOnly && agent != null) {
                AgentAvatar(agent.avatar)
            } else {
                SabaBrandMark(size = 72.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text(context.getString(R.string.saba_home_kicker),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = scheme.primary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (chatOnly && agent != null) agent.name else "صبا استودیو",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (!hasProvider) context.getString(R.string.saba_home_connect_model)
                else if (chatOnly && agent != null && agent.description.isNotBlank()) agent.description
                else context.getString(R.string.saba_home_question),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!hasProvider) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAddProvider) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.saba_home_connect_action))
                }
            } else {
                Spacer(Modifier.height(30.dp))
                if (chatOnly && agent == null && agentSetup != null) {
                    agentSetup()
                    Spacer(Modifier.height(12.dp))
                }
                Text(context.getString(R.string.saba_home_quick_start),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp))
                ideas.forEachIndexed { index, (icon, label) ->
                    Surface(
                        color = scheme.surface.copy(alpha = 0.97f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(
                            1.dp,
                            (if (index == 0) cyan else scheme.outlineVariant)
                                .copy(alpha = if (index == 0) 0.48f else 0.55f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .heightIn(min = 60.dp)
                            .clickable(role = Role.Button, onClick = { onSuggestion(label) }),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        ) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                                .background((if (index == 0) scheme.primaryContainer else scheme.secondaryContainer).copy(alpha = 0.84f)),
                                contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = if (index == 0) scheme.primary else scheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = label,
                                color = scheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// Verified creator profile: Sanuu, creator of AndroidHarness.
private const val DONATION_URL = "https://buymeacoffee.com/sanuj7"

@Composable
private fun DonationRow() {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = uiContext.getString(R.string.ui_open_buy_me_a_coffee)) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        context,
                        uiContext.getString(R.string.ui_no_browser_available_visit_buymeacoffee_com_sanuj7),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFFFFDD00)),
        ) {
            // Official asset: https://cdn.buymeacoffee.com/assets/logos/icon-black.png
            Image(
                painterResource(R.drawable.ic_buy_me_a_coffee),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(uiContext.getString(R.string.ui_donate), style = MaterialTheme.typography.bodyMedium)
            Text(
                uiContext.getString(R.string.ui_help_us_keep_building),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.OpenInNew,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
    }
}
