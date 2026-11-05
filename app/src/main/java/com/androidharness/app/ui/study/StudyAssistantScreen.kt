package com.androidharness.app.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.androidharness.app.R
import com.androidharness.app.study.StudyPractice

/** Stateless on-device study workspace. No provider, file or other project is queried. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyAssistantScreen(onBack: () -> Unit) {
    var notes by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("flashcards") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val limitError = stringResource(R.string.study_limit)
    val invalidNotesError = stringResource(R.string.study_invalid_notes)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.study_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.study_back))
                        }
                    },
                )
            },
        ) { inner ->
            Column(
                Modifier.fillMaxSize().padding(inner).imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.AutoStories, contentDescription = null)
                            Text(stringResource(R.string.study_hero), style = MaterialTheme.typography.titleLarge)
                        }
                        Text(stringResource(R.string.study_subtitle), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { text ->
                        if (text.length <= StudyPractice.MAX_SOURCE_CHARS) {
                            notes = text
                            result = null
                            error = null
                        } else error = limitError
                    },
                    label = { Text(stringResource(R.string.study_notes)) },
                    placeholder = { Text(stringResource(R.string.study_example)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 12,
                )
                Text(stringResource(R.string.study_notice), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "flashcards" to R.string.study_flashcards,
                        "quiz" to R.string.study_quiz,
                        "plan" to R.string.study_plan,
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = mode == value,
                            onClick = { mode = value; result = null; error = null },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Button(
                    onClick = {
                        val produced = runCatching { StudyPractice.render(notes, mode) }
                        result = produced.getOrNull()
                        error = if (produced.isFailure) invalidNotesError else null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = notes.isNotBlank(),
                ) {
                    Text(stringResource(R.string.study_generate))
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                result?.let { generated ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.study_result), style = MaterialTheme.typography.titleMedium)
                            Text(generated, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
