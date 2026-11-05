package com.androidharness.app.ui.library

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A bounded, app-private source index. Only text is indexed; SAF grants are explicit. */
internal data class LibrarySource(val id: String, val name: String, val uri: String, val text: String)
internal object SourceIndex {
    private const val FILE = "saba_private_sources.json"
    private const val MAX_BYTES = 1_000_000
    fun read(context: android.content.Context): List<LibrarySource> = runCatching {
        val file = java.io.File(context.filesDir, FILE)
        if (!file.exists() || file.length() > 12_000_000) return emptyList()
        val json = JSONArray(file.readText())
        (0 until json.length()).map { i ->
            json.getJSONObject(i).let { LibrarySource(it.getString("id"), it.getString("name"), it.getString("uri"), it.getString("text")) }
        }
    }.getOrDefault(emptyList())
    fun add(context: android.content.Context, uri: Uri): LibrarySource {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_BYTES + 1) }
            ?: error("فایل قابل خواندن نیست")
        require(bytes.size <= MAX_BYTES) { "حداکثر حجم متن یک مگابایت است" }
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains('\u0000')) { "فقط فایل متنی UTF-8 پذیرفته می‌شود" }
        val name = uri.lastPathSegment?.substringAfterLast('/')?.take(120) ?: "منبع"
        val source = LibrarySource(UUID.randomUUID().toString(), name, uri.toString(), text)
        val updated = read(context) + source
        require(updated.size <= 100) { "حداکثر ۱۰۰ منبع" }
        val json = JSONArray()
        updated.forEach { json.put(JSONObject().put("id", it.id).put("name", it.name).put("uri", it.uri).put("text", it.text)) }
        val tmp = java.io.File(context.filesDir, "$FILE.tmp")
        tmp.writeText(json.toString())
        require(tmp.renameTo(java.io.File(context.filesDir, FILE))) { "ذخیره‌سازی ناموفق بود" }
        return source
    }
    fun find(sources: List<LibrarySource>, query: String): List<Pair<LibrarySource, String>> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return sources.mapNotNull { source ->
            val i = source.text.indexOf(q, ignoreCase = true)
            if (i < 0) null else source to source.text.substring((i - 100).coerceAtLeast(0), (i + q.length + 180).coerceAtMost(source.text.length))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryDesignScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var sources by remember { mutableStateOf(SourceIndex.read(context)) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<LibrarySource?>(null) }
    var designTitle by remember { mutableStateOf("صبا استودیو") }
    var designColor by remember { mutableStateOf("#2563EB") }
    var exportSvg by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { SourceIndex.add(context, uri) }
                sources = withContext(Dispatchers.IO) { SourceIndex.read(context) }
                message = "منبع به کتابخانه خصوصی افزوده شد"
            } catch (e: Exception) { message = e.message ?: "خطای ورود فایل" }
        }
    }
    val saveSvg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(exportSvg.toByteArray(Charsets.UTF_8)) }
                        ?: error("خروجی باز نشد")
                }
                message = "فایل SVG ذخیره شد"
            } catch (e: Exception) { message = e.message ?: "خطای ذخیره" }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("کتابخانه و طراحی صبا") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("بازگشت") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("کتابخانه و جستجو") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("طراحی SVG") })
            }
            if (tab == 0) {
                Button(onClick = { picker.launch(arrayOf("text/plain", "text/markdown", "text/*")) }) { Text("افزودن منبع متنی از دستگاه") }
                OutlinedTextField(value = query, onValueChange = { query = it.take(150) }, label = { Text("جستجو در محتوای منابع") }, modifier = Modifier.fillMaxWidth())
                Text("نتایج همراه با نام منبع و گزیده متن؛ پاسخ هوش مصنوعی تولید نمی‌شود.", style = MaterialTheme.typography.bodySmall)
                val matches = remember(sources, query) { SourceIndex.find(sources, query) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (query.length < 2) items(sources, key = { it.id }) { s ->
                        OutlinedCard(onClick = { selected = s }) { Text(s.name, Modifier.padding(12.dp)) }
                    } else items(matches, key = { it.first.id }) { (s, excerpt) ->
                        OutlinedCard(onClick = { selected = s }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(s.name, style = MaterialTheme.typography.titleSmall)
                                Text(excerpt, maxLines = 5)
                            }
                        }
                    }
                }
            } else {
                Text("ویرایشگر ساده و قابل خروجی‌گیری SVG؛ بدون ادعای روتوش یا تولید تصویر AI")
                OutlinedTextField(designTitle, { designTitle = it.take(80) }, label = { Text("عنوان طرح") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(designColor, { designColor = it.take(7) }, label = { Text("رنگ HEX مانند #2563EB") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    if (!Regex("#[0-9a-fA-F]{6}").matches(designColor)) { message = "کد رنگ نامعتبر است" }
                    else {
                        val safe = designTitle.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
                        exportSvg = """<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1080" viewBox="0 0 1080 1080"><rect width="1080" height="1080" fill="$designColor"/><text x="540" y="540" text-anchor="middle" dominant-baseline="middle" font-family="sans-serif" font-size="64" fill="white">$safe</text></svg>"""
                        saveSvg.launch("saba-design.svg")
                    }
                }) { Text("ذخیره طرح SVG قابل ویرایش") }
            }
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
    selected?.let { source ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(source.name) },
            text = { Text(source.text.take(2500)) }, confirmButton = { TextButton(onClick = { selected = null }) { Text("بستن") } })
    }
}
