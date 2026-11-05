package com.androidharness.app.ui.documents

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.androidharness.app.R
import com.androidharness.app.documents.*
import com.androidharness.app.ui.film.FilmStudioScreen
import com.androidharness.app.ui.common.AppHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit user export. Does not publish to a project or execute document content. */
@Composable
fun DocumentStudioScreen(onBack: () -> Unit, onSaved: (Uri, String, String) -> Unit = { _, _, _ -> }) {
    var filmStudioOpen by rememberSaveable { mutableStateOf(false) }
    if (filmStudioOpen) {
        FilmStudioScreen(onBack = { filmStudioOpen = false })
        return
    }
    val context = LocalContext.current
    val store = remember(context.applicationContext) { DocumentStudioStore(context) }
    val scope = rememberCoroutineScope()
    var format by rememberSaveable { mutableStateOf("pdf") }
    // Deliberately not saved in the Activity bundle: large/user-entered text stays in memory only.
    var title by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var staged by rememberSaveable { mutableStateOf("") }
    var hash by rememberSaveable { mutableStateOf("") }
    var mime by rememberSaveable { mutableStateOf("") }
    var filename by rememberSaveable { mutableStateOf("") }
    var pickerPending by rememberSaveable { mutableStateOf(false) }
    var saveInProgress by rememberSaveable { mutableStateOf(false) }
    var savedUri by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var source by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val previewOwner = remember { DocumentPreviewOwner() }
    DisposableEffect(previewOwner) { onDispose { previewOwner.close() } }
    fun invalidatePreview() {
        val previous = staged
        if (previous.isNotEmpty()) {
            staged = ""; hash = ""; mime = ""; filename = ""; savedUri = ""
            bitmap = null; source = ""
            previewOwner.replace(null)
            scope.launch(Dispatchers.IO) { store.delete(previous) }
        }
    }
    val failed = stringResource(R.string.ds_failed)
    val verified = stringResource(R.string.ds_verified)
    val cancelled = stringResource(R.string.ds_cancelled)
    suspend fun loadPreview() {
        loadOwnedDocumentPreview(create = { store.preview(staged, hash, mime) }) { preview ->
            previewOwner.replace(preview)
            bitmap = preview
        }
        source = withContext(Dispatchers.IO) { store.previewText(staged, hash, mime) }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { store.cleanup() }
        if (saveInProgress) { saveInProgress = false; message = failed }
        if (staged.isNotEmpty()) {
            busy = true
            try { loadPreview() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { staged = ""; pickerPending = false; message = "$failed ${e.message.orEmpty()}" }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val wasPending = pickerPending
        pickerPending = false
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) { message = cancelled }
        else if (!wasPending || staged.isEmpty() || hash.isEmpty()) { message = failed }
        else {
            busy = true; saveInProgress = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { store.save(uri, staged, hash) }
                    savedUri = uri.toString(); message = verified
                    onSaved(uri, mime, filename)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = "$failed ${e.message.orEmpty()}" }
                finally { busy = false; saveInProgress = false }
            }
        }
    }
    Scaffold(topBar = { AppHeader(stringResource(R.string.ds_title), onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ds_scope), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { filmStudioOpen = true }) {
                Text(stringResource(R.string.film_entry))
            }
            Row(Modifier.fillMaxWidth().horizontalScrollCompat()) {
                DocumentEncoder.mimeTypes.keys.forEach { type ->
                    FilterChip(selected = format == type, enabled = !busy && !pickerPending, onClick = { if (format != type) { invalidatePreview(); format = type } }, label = { Text(type.uppercase()) })
                    Spacer(Modifier.width(6.dp))
                }
            }
            OutlinedTextField(value = title, onValueChange = { if (it.length <= 120) { if (it != title) invalidatePreview(); title = it } else message = failed }, enabled = !busy && !pickerPending,
                label = { Text(stringResource(R.string.ds_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text(stringResource(R.string.ds_input_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = input, onValueChange = { if (it.length <= 100_000) { if (it != input) invalidatePreview(); input = it } else message = failed }, enabled = !busy && !pickerPending,
                label = { Text(stringResource(R.string.ds_content)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), minLines = 5)
            Button(enabled = !busy && !pickerPending, modifier = Modifier.fillMaxWidth(), onClick = {
                if (!busy && !pickerPending) {
                    busy = true; message = ""
                    val selectedFormat = format; val selectedTitle = title; val selectedInput = input
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val spec = DocumentStudioInput.parse(selectedFormat, selectedTitle, selectedInput)
                                store.generate(spec)
                            }
                            val previous = staged
                            previewOwner.replace(null); bitmap = null; source = ""; savedUri = ""
                            staged = result.first; hash = result.second
                            mime = DocumentEncoder.mimeTypes.getValue(selectedFormat)
                            filename = DocumentStudioInput.fileName(selectedTitle, selectedFormat)
                            if (previous.isNotEmpty()) withContext(Dispatchers.IO) { store.delete(previous) }
                            loadPreview()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = "$failed ${e.message.orEmpty()}" }
                        finally { busy = false }
                    }
                }
            }) { Text(stringResource(R.string.ds_generate)) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (staged.isNotEmpty()) {
                Text(filename, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ds_preview_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.ds_preview), modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) }
                if (source.isNotEmpty()) SelectionContainer {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        Text(source, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(enabled = !busy && !pickerPending, modifier = Modifier.fillMaxWidth(), onClick = {
                    if (!busy && !pickerPending) {
                        pickerPending = true
                        try { picker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = mime; putExtra(Intent.EXTRA_TITLE, filename)
                        }) } catch (e: Exception) { pickerPending = false; message = "$failed ${e.message.orEmpty()}" }
                    }
                }) { Text(stringResource(R.string.ds_save)) }
            }
            if (savedUri.isNotEmpty()) OutlinedButton(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(savedUri), mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
                catch (e: Exception) { message = "$failed ${e.message.orEmpty()}" }
            }) { Text(stringResource(R.string.ds_open)) }
            if (message.isNotEmpty()) Text(message)
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))
