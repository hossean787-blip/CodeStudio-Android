package com.androidharness.app.ui.film

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.androidharness.app.R
import com.androidharness.app.film.FilmCutProject
import com.androidharness.app.ui.common.AppHeader
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Deliberately limited to one local, user-selected video: real trim, MP4 and portable project. */
@OptIn(UnstableApi::class)
@Composable
fun FilmStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("Saba film") }
    var startSeconds by rememberSaveable { mutableStateOf("0") }
    var endSeconds by rememberSaveable { mutableStateOf("") }
    var durationMs by rememberSaveable { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Transformer?>(null) }
    var output by remember { mutableStateOf<File?>(null) }
    fun project(): FilmCutProject? {
        val start = startSeconds.trim().toLongOrNull()?.takeIf { it in 0L..21_600L } ?: return null
        val end = endSeconds.trim().toLongOrNull()?.takeIf { it in 1L..21_600L } ?: return null
        return FilmCutProject(title = title.trim(), sourceUri = source,
            startMs = start * 1000L, endMs = end * 1000L)
    }
    fun invalidateRender() {
        if (!exporting) { output?.delete(); output = null }
    }
    val current = project()
    val validationError = current?.validationError(durationMs.takeIf { it > 0 })
    val ready = source.isNotEmpty() && current != null && validationError == null

    val chooseVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !exporting) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            invalidateRender()
            source = uri.toString()
            durationMs = 0L
            endSeconds = ""
            message = ""
            scope.launch {
                val length = withContext(Dispatchers.IO) {
                    runCatching {
                        val reader = MediaMetadataRetriever()
                        try {
                            reader.setDataSource(context, uri)
                            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        } finally { reader.release() }
                    }.getOrDefault(0L)
                }
                if (source == uri.toString()) {
                    durationMs = length
                    endSeconds = if (length > 0) (length / 1000L).toString() else ""
                    if (length <= 0) message = context.getString(R.string.film_duration_unavailable)
                }
            }
        }
    }
    val saveProject = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val spec = project()
        if (uri != null && spec != null && spec.validationError() == null) {
            scope.launch {
                try {
                    val bytes = spec.toPortableJson().toByteArray(Charsets.UTF_8)
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "w")
                            ?: error("Cannot open the selected project document.")
                        stream.use { it.write(bytes); it.flush() }
                    }
                    message = context.getString(R.string.film_project_saved)
                } catch (_: Exception) { message = context.getString(R.string.film_save_failed) }
            }
        }
    }
    val saveVideo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val rendered = output
        if (uri != null && rendered != null && rendered.isFile && rendered.length() > 0) {
            scope.launch {
                try {
                    val copied = withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "w")
                            ?: error("Cannot open the selected video destination.")
                        stream.use { destination -> rendered.inputStream().use { input -> input.copyTo(destination) } }
                    }
                    message = if (copied == rendered.length()) context.getString(R.string.film_mp4_saved)
                        else context.getString(R.string.film_save_failed)
                } catch (_: Exception) { message = context.getString(R.string.film_save_failed) }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            active?.cancel()
            output?.delete()
        }
    }

    Scaffold(topBar = { AppHeader(stringResource(R.string.film_title), onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.film_scope), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(enabled = !exporting, modifier = Modifier.fillMaxWidth(),
                onClick = { chooseVideo.launch(arrayOf("video/*")) }) {
                Text(stringResource(R.string.film_choose_video))
            }
            if (source.isNotEmpty()) {
                AndroidView(factory = { ctx -> VideoView(ctx).apply {
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                } }, update = { view -> if (view.tag != source) { view.tag = source; view.setVideoURI(Uri.parse(source)) } },
                    modifier = Modifier.fillMaxWidth().height(220.dp))
                if (durationMs > 0) Text(stringResource(R.string.film_duration, durationMs / 1000L))
            }
            OutlinedTextField(value = title, onValueChange = { if (it.length <= 120) { invalidateRender(); title = it } },
                label = { Text(stringResource(R.string.film_name)) }, enabled = !exporting,
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = startSeconds, onValueChange = { invalidateRender(); startSeconds = it },
                    label = { Text(stringResource(R.string.film_start)) }, enabled = !exporting,
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = endSeconds, onValueChange = { invalidateRender(); endSeconds = it },
                    label = { Text(stringResource(R.string.film_end)) }, enabled = !exporting,
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            if (source.isNotEmpty() && !ready) Text(
                stringResource(R.string.film_invalid_cut), color = MaterialTheme.colorScheme.error)
            OutlinedButton(enabled = ready && !exporting, modifier = Modifier.fillMaxWidth(), onClick = {
                if (current != null) saveProject.launch("saba-film-project.json")
            }) { Text(stringResource(R.string.film_save_project)) }
            Button(enabled = ready && !exporting && durationMs > 0, modifier = Modifier.fillMaxWidth(), onClick = {
                val spec = current ?: return@Button
                val dir = File(context.cacheDir, "saba-film-exports").apply { mkdirs() }
                val candidate = File(dir, UUID.randomUUID().toString() + ".mp4")
                exporting = true
                message = ""
                try {
                    val input = MediaItem.Builder().setUri(Uri.parse(spec.sourceUri))
                        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(spec.startMs).setEndPositionMs(spec.endMs).build()).build()
                    active = Transformer.Builder(context).addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            active = null
                            exporting = false
                            if (candidate.isFile && candidate.length() > 0) {
                                output?.delete()
                                output = candidate
                                message = context.getString(R.string.film_export_ready)
                            } else {
                                candidate.delete()
                                message = context.getString(R.string.film_export_failed)
                            }
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            active = null
                            exporting = false
                            candidate.delete()
                            message = context.getString(R.string.film_export_failed)
                        }
                    }).build().also { it.start(input, candidate.absolutePath) }
                } catch (_: Exception) {
                    candidate.delete()
                    active = null
                    exporting = false
                    message = context.getString(R.string.film_export_failed)
                }
            }) { Text(stringResource(R.string.film_export)) }
            if (exporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = {
                    active?.cancel(); active = null
                    exporting = false
                    message = context.getString(R.string.film_cancelled)
                }) { Text(stringResource(R.string.film_cancel)) }
            }
            if (output?.isFile == true && !exporting) Button(modifier = Modifier.fillMaxWidth(),
                onClick = { saveVideo.launch("saba-film.mp4") }) {
                Text(stringResource(R.string.film_save_mp4))
            }
            if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
