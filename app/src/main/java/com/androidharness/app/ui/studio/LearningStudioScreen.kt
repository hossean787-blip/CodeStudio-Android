package com.androidharness.app.ui.studio

import android.content.Intent
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
import androidx.compose.ui.unit.dp
import com.androidharness.app.studio.LearningToolEngine
import com.androidharness.app.studio.StudioTool
import com.androidharness.app.ui.common.AppHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var toolName by rememberSaveable { mutableStateOf(StudioTool.MIND_MAP.name) }
    val tool = StudioTool.valueOf(toolName)
    var menu by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var input by rememberSaveable { mutableStateOf("") }
    var output by rememberSaveable { mutableStateOf("") }
    var extension by rememberSaveable { mutableStateOf("md") }
    var mime by rememberSaveable { mutableStateOf("text/markdown") }
    var message by rememberSaveable { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri == null) message = "ذخیره لغو شد."
        else runCatching { context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(output.toByteArray()) } }
            .onSuccess { message = "فایل ذخیره و دوباره قابل ویرایش است." }
            .onFailure { message = "ذخیره ناموفق: ${it.message.orEmpty()}" }
    }
    Scaffold(topBar = { AppHeader("ابزارهای آموزشی و تخصصی", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("۲۳ مسیر مصوب در یک استودیو؛ پردازش‌های محلی آفلاین هستند و اتصال AI/وب تا پیکربندی واقعی، انجام‌شده اعلام نمی‌شود.",
                style = MaterialTheme.typography.bodyMedium)
            ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                OutlinedTextField(tool.titleFa, {}, readOnly = true, label = { Text(tool.groupFa) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    StudioTool.entries.forEach { candidate -> DropdownMenuItem(
                        text = { Text("${candidate.groupFa} — ${candidate.titleFa}") },
                        onClick = { toolName = candidate.name; menu = false; output = ""; message = "" }) }
                }
            }
            OutlinedTextField(title, { if (it.length <= 120) title = it }, label = { Text("نام پروژه") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(input, { if (it.length <= 100_000) input = it },
                label = { Text(inputLabel(tool)) }, supportingText = { Text(inputHelp(tool)) },
                minLines = 7, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                runCatching { LearningToolEngine.run(tool, title, input) }
                    .onSuccess { output = it.content; extension = it.extension; mime = it.mime; message = "خروجی محلی ساخته شد." }
                    .onFailure { message = it.message.orEmpty() }
            }, modifier = Modifier.fillMaxWidth()) { Text("ساخت خروجی قابل ویرایش") }
            if (output.isNotEmpty()) {
                OutlinedTextField(output, { output = it }, label = { Text("پیش‌نمایش و ویرایش") },
                    minLines = 10, modifier = Modifier.fillMaxWidth())
                Button(onClick = { exporter.launch(safeFileName(title.ifBlank { tool.titleFa }) + "." + extension) },
                    modifier = Modifier.fillMaxWidth()) { Text("ذخیره فایل استاندارد") }
            }
            if (tool == StudioTool.VOICE_NOTES) Text("برای تبدیل گفتار، از ورودی میکروفون گفت‌وگو استفاده کنید؛ Android پیش از ضبط مجوز را نمایش می‌دهد.")
            if (tool == StudioTool.CODE_SANDBOX) Text("اجرای واقعی فقط از Terminal یا Build & Test و پس از مرور فرمان و سطح دسترسی انجام می‌شود.")
            if (tool in setOf(StudioTool.DEEP_RESEARCH, StudioTool.RESEARCH_SPACE, StudioTool.GITHUB_ANALYSIS))
                Text("این خروجی برنامه/گزارش محلی است. نتیجه آنلاین بدون اتصال واقعی Provider یا GitHub تولید نمی‌شود.")
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun inputLabel(tool: StudioTool) = when (tool) {
    StudioTool.FLASHCARDS -> "هر خط: پرسش | پاسخ"
    StudioTool.SUBTITLES -> "هر خط یک قطعه زیرنویس"
    StudioTool.API_COST -> "توکن ورودی، توکن خروجی، نرخ ورودی، نرخ خروجی"
    StudioTool.BOOK_CHAT, StudioTool.RESEARCH_NOTEBOOK -> "گزیده‌های منبع؛ هر خط یک ارجاع"
    else -> "محتوا یا شرح کار؛ هر مورد در یک خط"
}

private fun inputHelp(tool: StudioTool) = when (tool) {
    StudioTool.STUDY_CALENDAR -> "هر خط یک رویداد مستقل در فایل ICS می‌شود."
    StudioTool.CODE_SECURITY -> "کد فقط به‌صورت محلی و الگویی بررسی می‌شود و اجرا نخواهد شد."
    StudioTool.SUBTITLES -> "زمان‌بندی اولیه پنج‌ثانیه‌ای ساخته می‌شود و پیش از ذخیره قابل ویرایش است."
    else -> "ورودی روی دستگاه پردازش می‌شود؛ حداکثر ۱۰۰هزار نویسه."
}

private fun safeFileName(value: String) = value.trim().replace(Regex("[^\\p{L}\\p{N}._-]+"), "-").trim('-').take(80).ifEmpty { "saba-studio" }
