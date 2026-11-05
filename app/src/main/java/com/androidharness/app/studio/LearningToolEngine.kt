package com.androidharness.app.studio

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class StudioTool(val titleFa: String, val groupFa: String) {
    EDUCATIONAL_VIDEO("ویدئوی آموزشی", "آموزشی"),
    INTERACTIVE_TUTOR("معلم هوشمند تعاملی", "آموزشی"),
    MIND_MAP("نقشه ذهنی", "آموزشی"),
    TIMED_QUIZ("آزمون زمان‌دار", "آموزشی"),
    FLASHCARDS("فلش‌کارت و مرور", "آموزشی"),
    BOOK_CHAT("گفت‌وگو با کتاب و ارجاع", "آموزشی"),
    VOICE_NOTES("یادداشت صوتی و گفتار به متن", "آموزشی"),
    THESIS("پایان‌نامه و پژوهش", "آموزشی"),
    SUBTITLES("زیرنویس", "آموزشی"),
    STUDY_CALENDAR("تقویم آزمون و مطالعه", "آموزشی"),
    DEEP_RESEARCH("پژوهش عمیق", "عمومی"),
    MULTI_AGENT("اجرای چندعامل", "عمومی"),
    SCHEDULING("زمان‌بندی فعالیت‌ها", "عمومی"),
    CODE_SANDBOX("اجرای کنترل‌شده کد", "عمومی"),
    GITHUB_ANALYSIS("تحلیل مخزن GitHub", "عمومی"),
    CODE_SECURITY("بررسی امنیت کد", "عمومی"),
    VERSIONING("مدیریت نسخه‌ها", "عمومی"),
    API_COST("پایش هزینه API", "عمومی"),
    TUTOR_SPACE("محیط معلم تعاملی", "محیط تخصصی"),
    RESEARCH_NOTEBOOK("دفترچه پژوهشی با منابع", "محیط تخصصی"),
    AGENT_TEAM("تیم عامل‌های هوشمند", "محیط تخصصی"),
    FILE_STUDIO("استودیوی فایل", "محیط تخصصی"),
    RESEARCH_SPACE("محیط پژوهش عمیق", "محیط تخصصی"),
}

data class StudioResult(val mime: String = "text/markdown", val extension: String = "md", val content: String)

/** Deterministic, offline transformations. Network/AI work is never represented as completed here. */
object LearningToolEngine {
    private val stamp = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    fun run(tool: StudioTool, title: String, input: String, now: Instant = Instant.now()): StudioResult {
        val cleanTitle = title.trim().ifEmpty { tool.titleFa }
        val clean = input.trim()
        require(clean.isNotEmpty()) { "ورودی لازم است." }
        require(clean.length <= 100_000) { "ورودی بیش از حد مجاز است." }
        return when (tool) {
            StudioTool.MIND_MAP -> markdown(mindMap(cleanTitle, clean))
            StudioTool.FLASHCARDS -> markdown(flashcards(cleanTitle, clean))
            StudioTool.BOOK_CHAT -> markdown(citedNotebook(cleanTitle, clean))
            StudioTool.SUBTITLES -> StudioResult("application/x-subrip", "srt", subtitles(clean))
            StudioTool.STUDY_CALENDAR -> StudioResult("text/calendar", "ics", calendar(cleanTitle, clean, now))
            StudioTool.CODE_SECURITY -> markdown(securityReport(cleanTitle, clean))
            StudioTool.API_COST -> markdown(costReport(cleanTitle, clean))
            StudioTool.EDUCATIONAL_VIDEO -> markdown(videoPlan(cleanTitle, clean))
            StudioTool.TIMED_QUIZ -> markdown(quiz(cleanTitle, clean))
            StudioTool.THESIS -> markdown(thesis(cleanTitle, clean))
            StudioTool.INTERACTIVE_TUTOR, StudioTool.TUTOR_SPACE -> markdown(tutor(cleanTitle, clean))
            StudioTool.VOICE_NOTES -> markdown(voiceNotes(cleanTitle, clean))
            StudioTool.DEEP_RESEARCH, StudioTool.RESEARCH_SPACE -> markdown(researchPlan(cleanTitle, clean))
            StudioTool.MULTI_AGENT, StudioTool.AGENT_TEAM -> markdown(agentPlan(cleanTitle, clean))
            StudioTool.SCHEDULING -> markdown(schedule(cleanTitle, clean))
            StudioTool.CODE_SANDBOX -> markdown(codeSandbox(cleanTitle, clean))
            StudioTool.GITHUB_ANALYSIS -> markdown(githubAnalysis(cleanTitle, clean))
            StudioTool.VERSIONING -> markdown(versionManifest(cleanTitle, clean, now))
            StudioTool.RESEARCH_NOTEBOOK -> markdown(citedNotebook(cleanTitle, clean))
            StudioTool.FILE_STUDIO -> markdown(fileManifest(cleanTitle, clean))
        }
    }

    private fun markdown(text: String) = StudioResult(content = text)
    private fun lines(input: String) = input.lines().map(String::trim).filter(String::isNotEmpty)
    private fun bullets(input: String) = lines(input).joinToString("\n") { "- $it" }
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun mindMap(title: String, input: String): String = buildString {
        appendLine("# $title\n\n```mermaid\nmindmap\n  root(($title))")
        lines(input).forEach { row ->
            val depth = row.takeWhile { it == '-' || it == ' ' }.count().coerceIn(0, 6)
            append("    ".repeat(depth + 1)).appendLine(row.trimStart('-', ' '))
        }
        appendLine("```")
    }

    private fun flashcards(title: String, input: String): String = "# $title\n\n" + lines(input).mapIndexed { index, row ->
        val parts = row.split("|", limit = 2)
        "## کارت ${index + 1}\n\n**پرسش:** ${parts[0].trim()}\n\n**پاسخ:** ${parts.getOrElse(1) { "پاسخ وارد نشده" }.trim()}\n\nمرور بعدی: نامشخص تا ثبت ارزیابی کاربر"
    }.joinToString("\n\n")

    private fun citedNotebook(title: String, input: String): String = buildString {
        appendLine("# $title\n")
        lines(input).forEachIndexed { index, row -> appendLine("- $row [منبع ${index + 1}، خط ${index + 1}]") }
        appendLine("\n> ارجاع‌ها فقط به متن واردشده اشاره می‌کنند؛ اعتبار بیرونی منبع تأیید نشده است.")
    }

    private fun subtitles(input: String): String = lines(input).mapIndexed { index, row ->
        val start = index * 5
        val end = start + 4
        fun t(seconds: Int) = "00:${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')},000"
        "${index + 1}\n${t(start)} --> ${t(end)}\n$row"
    }.joinToString("\n\n", postfix = "\n")

    private fun calendar(title: String, input: String, now: Instant): String {
        val date = stamp.format(now).replace("-", "").replace(":", "").substringBefore('.')
        val events = lines(input).mapIndexed { index, row ->
            "BEGIN:VEVENT\r\nUID:${digest("$title-$index-$row").take(24)}@sabastudio\r\nDTSTAMP:$date\r\nSUMMARY:${row.replace("\n", " ")}\r\nDESCRIPTION:برنامه مطالعه ایجادشده در صبا استودیو\r\nEND:VEVENT"
        }
        return (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Saba Studio//Study Calendar//FA") + events + "END:VCALENDAR").joinToString("\r\n", postfix = "\r\n")
    }

    private fun securityReport(title: String, input: String): String {
        val rules = listOf(
            "کلید یا توکن احتمالی" to Regex("(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*[^\\s,;]{6,}"),
            "اجرای فرمان پوسته" to Regex("(?i)(Runtime\\.getRuntime|ProcessBuilder|exec\\s*\\()"),
            "WebView با JavaScript" to Regex("(?i)javaScriptEnabled\\s*=\\s*true"),
            "نشانی HTTP بدون TLS" to Regex("http://[^\\s\"']+"),
        )
        val findings = rules.flatMap { (name, regex) -> regex.findAll(input).map { "- $name در نویسه ${it.range.first}" }.toList() }
        return "# $title\n\n## یافته‌های ایستا\n${findings.ifEmpty { listOf("- الگوی پرخطر شناخته‌شده‌ای پیدا نشد.") }.joinToString("\n")}\n\n> این اسکن الگویی جایگزین بازبینی امنیتی یا تحلیل وابستگی‌ها نیست."
    }

    private fun costReport(title: String, input: String): String {
        val values = Regex("[-+]?\\d+(?:[.,]\\d+)?").findAll(input).map { it.value.replace(',', '.').toDouble() }.toList()
        val inputTokens = values.getOrElse(0) { 0.0 }; val outputTokens = values.getOrElse(1) { 0.0 }
        val inputRate = values.getOrElse(2) { 0.0 }; val outputRate = values.getOrElse(3) { 0.0 }
        val total = inputTokens / 1_000_000 * inputRate + outputTokens / 1_000_000 * outputRate
        return "# $title\n\n- توکن ورودی: ${inputTokens.toLong()}\n- توکن خروجی: ${outputTokens.toLong()}\n- هزینه برآوردی: ${"%.6f".format(java.util.Locale.US, total)}\n\n> نرخ‌ها باید توسط کاربر از تعرفه رسمی ارائه‌دهنده وارد شوند."
    }

    private fun videoPlan(title: String, input: String) = "# $title\n\n## سناریو و شات‌لیست\n" + lines(input).mapIndexed { i, it -> "${i + 1}. **نما ${i + 1}** — $it\n   - تصویر/اسلاید: تعیین شود\n   - گفتار: $it\n   - مدت پیشنهادی: ۸ ثانیه" }.joinToString("\n") + "\n\n> این خروجی پروژه ویدئو است، نه فایل ویدئوی رندرشده."
    private fun quiz(title: String, input: String) = "# $title\n\nزمان پیشنهادی: ${lines(input).size * 2} دقیقه\n\n" + lines(input).mapIndexed { i, it -> "${i + 1}. $it\n\nپاسخ: ______" }.joinToString("\n\n")
    private fun thesis(title: String, input: String) = "# $title\n\n## مسئله\n$input\n\n## ساختار پژوهش\n1. بیان مسئله و پیشینه\n2. پرسش‌ها و فرضیه‌ها\n3. روش‌شناسی و اخلاق پژوهش\n4. تحلیل داده\n5. نتیجه و محدودیت‌ها\n\n## منابع\n- منابع واقعی را با DOI/URL و تاریخ دسترسی ثبت کنید؛ منبع ساختگی تولید نشده است."
    private fun tutor(title: String, input: String) = "# $title\n\n## هدف یادگیری\n$input\n\n## گفت‌وگوی سقراطی\n1. اکنون چه می‌دانید؟\n2. کدام بخش مبهم است؟\n3. یک مثال نقض پیدا کنید.\n4. پاسخ را با زبان خود توضیح دهید.\n\n## سنجش\n- یک مثال حل‌شده\n- یک تمرین هدایت‌شده\n- یک تمرین مستقل"
    private fun voiceNotes(title: String, input: String) = "# $title\n\n## متن پیاده‌شده/واردشده\n$input\n\n## اقدام‌ها\n${bullets(input)}\n\n> تبدیل گفتار با مجوز صریح میکروفون و موتور موجود دستگاه انجام می‌شود؛ این فایل فقط متن تأییدشده را نگه می‌دارد."
    private fun researchPlan(title: String, input: String) = "# $title\n\n## پرسش\n$input\n\n## پروتکل\n1. تعریف دامنه و معیار ورود/خروج\n2. جست‌وجو در منابع اولیه و رسمی\n3. ثبت عبارت جست‌وجو و تاریخ\n4. ارزیابی اعتبار و تعارض منافع\n5. استخراج ادعا همراه نقل‌قول و URL/DOI\n6. ترکیب شواهد و ثبت عدم قطعیت\n\n> اتصال پژوهش آنلاین پیکربندی نشده است مگر کاربر Provider/ابزار وب را جداگانه فعال کند."
    private fun agentPlan(title: String, input: String) = "# $title\n\nهدف: $input\n\n| عامل | مسئولیت | مجوز | تحویل |\n|---|---|---|---|\n| پژوهشگر | گردآوری شواهد | فقط خواندن وب | منابع |\n| تحلیلگر | مقایسه ادعاها | بدون شبکه | تحلیل |\n| بازبین | کنترل کیفیت | فقط خواندن | ایرادها |\n| هماهنگ‌کننده | جمع‌بندی | بدون اجرای خودکار | گزارش |\n\nهر ابزار حساس پیش از اجرا به تأیید کاربر نیاز دارد."
    private fun schedule(title: String, input: String) = "# $title\n\n" + lines(input).mapIndexed { i, it -> "- [ ] روز ${i + 1}: $it" }.joinToString("\n")
    private fun codeSandbox(title: String, input: String) = "# $title\n\n## درخواست اجرا\n```text\n$input\n```\n\nوضعیت: **اجرا نشده**\n\nبرای اجرا از Terminal/Build & Test استفاده کنید؛ فرمان، پوشه کاری، شبکه و دسترسی فایل باید پیش از اجرا مرور و تأیید شوند."
    private fun githubAnalysis(title: String, input: String) = "# $title\n\nمخزن/شرح: `$input`\n\n- [ ] مالک و نام مخزن اعتبارسنجی شود\n- [ ] License و NOTICE بررسی شود\n- [ ] commit/tag مورد تحلیل ثبت شود\n- [ ] وابستگی‌ها و workflowها بدون اجرا بازرسی شوند\n- [ ] اسرار، کد بومی و مسیرهای ساخت بررسی شوند\n\nوضعیت اتصال: تا import واقعی از بخش GitHub، **تحلیل آنلاین انجام نشده**."
    private fun versionManifest(title: String, input: String, now: Instant) = "# $title\n\n- زمان UTC: ${stamp.format(now)}\n- SHA-256 محتوا: `${digest(input)}`\n- توضیح نسخه: $input"
    private fun fileManifest(title: String, input: String) = "# $title\n\n## فهرست فایل/وظیفه\n${bullets(input)}\n\nهیچ فایل خارجی بدون انتخاب صریح کاربر خوانده یا نوشته نشده است."
}
