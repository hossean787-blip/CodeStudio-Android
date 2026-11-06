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
    private const val MAX_BYTES = 1_000_000
    private const val MAX_INDEX_BYTES = 12_000_000L
    private fun scopedFile(context: android.content.Context, projectId: String): java.io.File =
        java.io.File(context.filesDir, LibraryScope.fileName(projectId))

    /** Legacy unscoped index is intentionally preserved, but never displayed or auto-imported. */
    fun hasLegacyIndex(context: android.content.Context): Boolean =
        java.io.File(context.filesDir, LibraryScope.LEGACY_FILE_NAME).exists()

    /** Invalid or oversized indices fail closed: never interpret corruption as an empty index. */
    fun read(context: android.content.Context, projectId: String): List<LibrarySource> {
        val file = scopedFile(context, projectId)
        if (!file.exists()) return emptyList()
        require(file.length() <= MAX_INDEX_BYTES) { "حجم کتابخانه از حد مجاز بیشتر است" }
        val json = JSONArray(file.readText(Charsets.UTF_8))
        require(json.length() <= 100) { "تعداد منابع کتابخانه نامعتبر است" }
        return (0 until json.length()).map { i ->
            json.getJSONObject(i).let {
                LibrarySource(it.getString("id"), it.getString("name"), it.getString("uri"), it.getString("text"))
            }
        }
    }

    fun add(context: android.content.Context, projectId: String, uri: Uri): LibrarySource {
        // Validate scope before any SAF access. An unassigned chat may not use a global index.
        val target = scopedFile(context, projectId)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_BYTES + 1) }
            ?: error("فایل قابل خواندن نیست")
        require(bytes.size <= MAX_BYTES) { "حداکثر حجم متن یک مگابایت است" }
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains('\u0000')) { "فقط فایل متنی UTF-8 پذیرفته می‌شود" }
        val name = uri.lastPathSegment?.substringAfterLast('/')?.take(120) ?: "منبع"
        val source = LibrarySource(UUID.randomUUID().toString(), name, uri.toString(), text)
        val updated = read(context, projectId) + source
        require(updated.size <= 100) { "حداکثر ۱۰۰ منبع" }
        val json = JSONArray()
        updated.forEach { json.put(JSONObject().put("id", it.id).put("name", it.name).put("uri", it.uri).put("text", it.text)) }
        val tmp = java.io.File.createTempFile("saba-sources-", ".tmp", context.filesDir)
        try {
            tmp.writeText(json.toString(), Charsets.UTF_8)
            java.nio.file.Files.move(tmp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tmp.delete()
        }
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
fun LibraryDesignScreen(projectId: String?, projectName: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    val scopeId = projectId?.takeIf { it.isNotBlank() }
    var sources by remember(scopeId) { mutableStateOf(emptyList<LibrarySource>()) }
    var query by remember(scopeId) { mutableStateOf("") }
    var message by remember(scopeId) { mutableStateOf("") }
    var selected by remember(scopeId) { mutableStateOf<LibrarySource?>(null) }
    var pendingImportProjectId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scopeId) {
        if (scopeId != null) {
            try {
                sources = withContext(Dispatchers.IO) { SourceIndex.read(context, scopeId) }
            } catch (e: Exception) {
                message = e.message ?: "خطا در خواندن کتابخانه پروژه"
            }
        }
    }
    var designTitle by remember { mutableStateOf("صبا استودیو") }
    var designColor by remember { mutableStateOf("#2563EB") }
    var exportSvg by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val selectedProject = pendingImportProjectId
        pendingImportProjectId = null
        if (uri != null && selectedProject != null && selectedProject == scopeId) scope.launch {
            try {
                withContext(Dispatchers.IO) { SourceIndex.add(context, selectedProject, uri) }
                if (selectedProject == scopeId) {
                    sources = withContext(Dispatchers.IO) { SourceIndex.read(context, selectedProject) }
                    message = "منبع به کتابخانه همین پروژه افزوده شد"
                }
            } catch (e: Exception) { message = e.message ?: "خطای ورود فایل" }
        } else if (uri != null) {
            message = "پروژه هنگام انتخاب فایل تغییر کرد؛ برای حفظ حریم خصوصی دوباره انتخاب کنید"
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
                if (scopeId == null) {
                    Text("برای نمایش یا افزودن منابع، ابتدا یک پروژه معتبر انتخاب کنید.")
                } else {
                    Text("کتابخانه اختصاصی: " + (projectName ?: "پروژه انتخاب‌شده"),
                        style = MaterialTheme.typography.titleSmall)
                    if (SourceIndex.hasLegacyIndex(context)) {
                        Text("منابع مشترک نسخه قدیمی برای جلوگیری از نشت بین پروژه‌ها حفظ ولی مخفی شده‌اند؛ انتقال فقط پس از بازبینی و تأیید دستی امکان‌پذیر خواهد بود.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = {
                        pendingImportProjectId = scopeId
                        picker.launch(arrayOf("text/plain", "text/markdown", "text/*"))
                    }) { Text("افزودن منبع متنی به همین پروژه") }
                    OutlinedTextField(value = query, onValueChange = { query = it.take(150) },
                        label = { Text("جستجو در منابع همین پروژه") }, modifier = Modifier.fillMaxWidth())
                    Text("نتایج همراه با نام منبع و گزیده متن؛ پاسخ هوش مصنوعی تولید نمی‌شود.",
                        style = MaterialTheme.typography.bodySmall)
                    val matches = remember(scopeId, sources, query) { SourceIndex.find(sources, query) }
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
