package com.androidharness.app.ui.documents

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import com.androidharness.app.ui.common.AppHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit user export. Does not publish to a project or execute document content. */
@Composable
fun DocumentStudioScreen(onBack: () -> Unit, onSaved: (Uri, String, String) -> Unit = { _, _, _ -> }) {
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
    DisposableEffect(bitmap) { val owned = bitmap; onDispose { owned?.recycle() } }
    val failed = stringResource(R.string.ds_failed)
    val verified = stringResource(R.string.ds_verified)
    val cancelled = stringResource(R.string.ds_cancelled)
    suspend fun loadPreview() {
        val preview = withContext(Dispatchers.IO) { store.preview(staged, hash, mime) }
        bitmap = preview
        source = if (mime == "text/plain" || mime == "image/svg+xml") withContext(Dispatchers.IO) { store.source(staged, hash) } else ""
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
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ds_scope))
            Row(Modifier.fillMaxWidth().horizontalScrollCompat()) {
                DocumentEncoder.mimeTypes.keys.forEach { type ->
                    FilterChip(selected = format == type, enabled = !busy && !pickerPending, onClick = { format = type }, label = { Text(type.uppercase()) })
                    Spacer(Modifier.width(6.dp))
                }
            }
            OutlinedTextField(value = title, onValueChange = { if (it.length <= 120) title = it else message = failed }, enabled = !busy && !pickerPending,
                label = { Text(stringResource(R.string.ds_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text(stringResource(R.string.ds_input_help))
            OutlinedTextField(value = input, onValueChange = { if (it.length <= 100_000) input = it else message = failed }, enabled = !busy && !pickerPending,
                label = { Text(stringResource(R.string.ds_content)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), minLines = 5)
            Button(enabled = !busy && !pickerPending, onClick = {
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
                            bitmap = null; source = ""; savedUri = ""
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
                Text(filename)
                Text(stringResource(R.string.ds_preview_help))
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.ds_preview), modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) }
                if (source.isNotEmpty()) Text(source)
                Button(enabled = !busy && !pickerPending, onClick = {
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
