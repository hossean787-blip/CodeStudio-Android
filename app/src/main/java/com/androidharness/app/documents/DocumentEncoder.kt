package com.androidharness.app.documents

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small native export model. No formulas, HTML, filesystem inputs or external resources. */
data class DocumentSpec(
    val format: String,
    val title: String = "",
    val text: String = "",
    val rows: List<List<String>> = emptyList(),
    val slides: List<String> = emptyList(),
    val entries: List<ArchiveText> = emptyList(),
    val labels: List<String> = emptyList(),
    val values: List<Double> = emptyList(),
    val width: Int = 800,
    val height: Int = 600,
)
data class ArchiveText(val name: String, val text: String)

object DocumentEncoder {
    const val MAX_OUTPUT = 8 * 1024 * 1024
    val mimeTypes = mapOf(
        "pdf" to "application/pdf", "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "zip" to "application/zip", "txt" to "text/plain", "code" to "text/plain",
        "svg" to "image/svg+xml", "png" to "image/png",
    )
    fun path(path: String): String {
        require(path.isNotBlank() && path.length <= 512 && !path.startsWith('/') && '\\' !in path && ':' !in path) { "A bounded relative path is required" }
        require(path.split('/').all { it.isNotEmpty() && it != "." && it != ".." && it.toByteArray().size <= 200 }) { "Unsafe path component" }
        require(path.none { it.code < 32 || it.code == 127 }) { "Control character in path" }
        validText(path)
        return path
    }
    fun validText(text: String) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            require(c == '\t' || c == '\n' || c == '\r' || c.code >= 32) { "Invalid XML control character" }
            require(c != '\uFFFE' && c != '\uFFFF') { "Invalid Unicode character" }
            if (c.isHighSurrogate()) {
                require(i + 1 < text.length && text[i + 1].isLowSurrogate()) { "Unpaired surrogate" }; i++
            } else require(!c.isLowSurrogate()) { "Unpaired surrogate" }
            i++
        }
    }
    fun validate(s: DocumentSpec) {
        require(s.format in mimeTypes) { "Unsupported document format" }
        require(s.title.length <= 200 && s.text.length <= 100_000) { "Title/text limit exceeded" }
        require(s.rows.size <= 1000 && s.rows.all { it.size <= 50 }) { "Sheet limit: 1000 rows, 50 columns" }
        require(s.slides.size <= 32 && s.slides.all { it.length <= 4000 }) { "Slide limit: 32 slides, 4000 characters each" }
        require(s.entries.size <= 100) { "Archive limit: 100 entries" }
        require(s.labels.size <= 32 && s.labels.all { it.length <= 80 }) { "Chart limit: 32 labels, 80 characters each" }
        require(s.values.size <= 32) { "Chart value limit exceeded" }
        require(s.width in 320..1600 && s.height in 240..1200) { "Chart dimensions out of range" }
        require(s.values.all { it.isFinite() && it >= 0 && it <= 1e12 }) { "Chart values must be finite, nonnegative and at most 1e12" }
        val strings = listOf(s.title, s.text) + s.rows.flatten() + s.slides + s.entries.flatMap { listOf(it.name, it.text) } + s.labels
        require(strings.all { it.length <= 100_000 } && strings.sumOf { it.length.toLong() } <= 1_000_000) { "Input text limit exceeded" }
        strings.forEach(::validText)
        s.entries.forEach { path(it.name); require(it.text.toByteArray(Charsets.UTF_8).size <= 256 * 1024) { "Archive entry too large" } }
        require(s.entries.map { it.name }.distinct().size == s.entries.size) { "Duplicate archive entry" }
        val names = s.entries.map { it.name }.toSet()
        require(names.none { n -> n.split('/').dropLast(1).indices.any { i -> n.split('/').take(i + 1).joinToString("/") in names } }) { "Archive file/directory collision" }
        when (s.format) {
            "xlsx" -> require(s.rows.isNotEmpty()) { "Sheet rows required" }
            "pptx" -> require(s.slides.isNotEmpty()) { "Slides required" }
            "zip" -> require(s.entries.isNotEmpty()) { "Archive entries required" }
            "svg", "png" -> require(s.labels.isNotEmpty() && s.labels.size == s.values.size) { "Matching chart labels and values required" }
        }
    }
    fun xml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\r", "&#13;").replace("\"", "&quot;").replace("'", "&apos;")
    private const val HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val OFFICE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private fun rels(vararg rel: Triple<String, String, String>) = HEAD + "<Relationships xmlns=\"$REL\">" + rel.joinToString("") { (id, type, target) -> "<Relationship Id=\"$id\" Type=\"$OFFICE/$type\" Target=\"$target\"/>" } + "</Relationships>"
    private fun types(parts: Map<String, String>) = HEAD + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" + parts.entries.joinToString("") { (part, type) -> "<Override PartName=\"/$part\" ContentType=\"application/vnd.openxmlformats-officedocument.$type+xml\"/>" } + "</Types>"
    fun encode(s: DocumentSpec): ByteArray {
        validate(s)
        val result = when (s.format) {
            "txt", "code" -> s.text.toByteArray(Charsets.UTF_8)
            "zip" -> zip(s.entries.associate { it.name to it.text })
            "docx" -> docx(s)
            "xlsx" -> xlsx(s)
            "pptx" -> pptx(s)
            "svg" -> svg(s).toByteArray(Charsets.UTF_8)
            else -> error("${s.format} requires the Android renderer")
        }
        require(result.size <= MAX_OUTPUT) { "Output too large" }
        return result
    }
    private fun zip(parts: Map<String, String>): ByteArray {
        val out = BoundedDocumentOutput()
        ZipOutputStream(out).use { z -> parts.forEach { (name, text) ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            val crc = java.util.zip.CRC32().apply { update(bytes) }
            // STORED avoids compressor/platform variability and has predictable bounded size.
            val entry = ZipEntry(name).apply { time = 0L; method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; this.crc = crc.value }
            z.putNextEntry(entry); z.write(bytes); z.closeEntry()
            require(out.size() <= MAX_OUTPUT) { "Output too large" }
        } }
        return out.toByteArray()
    }
    private fun docx(s: DocumentSpec): ByteArray {
        val body = (listOf(s.title) + s.text.split('\n')).joinToString("") {
            "<w:p><w:pPr><w:bidi/></w:pPr><w:r><w:rPr><w:rtl/></w:rPr><w:t xml:space=\"preserve\">${xml(it)}</w:t></w:r></w:p>"
        }
        return zip(linkedMapOf(
            "[Content_Types].xml" to types(mapOf("word/document.xml" to "wordprocessingml.document.main")),
            "_rels/.rels" to rels(Triple("rId1", "officeDocument", "word/document.xml")),
            "word/document.xml" to "$HEAD<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>",
        ))
    }
    private fun xlsx(s: DocumentSpec): ByteArray {
        val rows = s.rows.mapIndexed { r, cells -> "<row r=\"${r + 1}\">" + cells.mapIndexed { c, text ->
            var n = c + 1; var col = ""; while (n > 0) { n--; col = ('A' + n % 26) + col; n /= 26 }
            "<c r=\"$col${r + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(text)}</t></is></c>"
        }.joinToString("") + "</row>" }.joinToString("")
        return zip(linkedMapOf(
            "[Content_Types].xml" to types(mapOf("xl/workbook.xml" to "spreadsheetml.sheet.main", "xl/worksheets/sheet1.xml" to "spreadsheetml.worksheet")),
            "_rels/.rels" to rels(Triple("rId1", "officeDocument", "xl/workbook.xml")),
            "xl/workbook.xml" to "$HEAD<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"$OFFICE\"><sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to rels(Triple("rId1", "worksheet", "worksheets/sheet1.xml")),
            "xl/worksheets/sheet1.xml" to "$HEAD<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"/></sheetViews><sheetData>$rows</sheetData></worksheet>",
        ))
    }
    private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private fun tree(body: String) = "<p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>$body</p:spTree>"
    private fun pptx(s: DocumentSpec): ByteArray {
        val partTypes = linkedMapOf("ppt/presentation.xml" to "presentationml.presentation.main", "ppt/slideMasters/slideMaster1.xml" to "presentationml.slideMaster", "ppt/slideLayouts/slideLayout1.xml" to "presentationml.slideLayout", "ppt/theme/theme1.xml" to "theme")
        s.slides.indices.forEach { partTypes["ppt/slides/slide${it + 1}.xml"] = "presentationml.slide" }
        val ns = "xmlns:p=\"$P\" xmlns:a=\"$A\" xmlns:r=\"$OFFICE\""
        val parts = linkedMapOf(
            "[Content_Types].xml" to types(partTypes),
            "_rels/.rels" to rels(Triple("rId1", "officeDocument", "ppt/presentation.xml")),
            "ppt/presentation.xml" to "$HEAD<p:presentation $ns><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst>" + s.slides.indices.joinToString("") { "<p:sldId id=\"${256 + it}\" r:id=\"rId${it + 2}\"/>" } + "</p:sldIdLst><p:sldSz cx=\"9144000\" cy=\"6858000\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>",
            "ppt/_rels/presentation.xml.rels" to rels(*(listOf(Triple("rId1", "slideMaster", "slideMasters/slideMaster1.xml")) + s.slides.indices.map { Triple("rId${it + 2}", "slide", "slides/slide${it + 1}.xml") }).toTypedArray()),
            "ppt/slideMasters/slideMaster1.xml" to "$HEAD<p:sldMaster $ns><p:cSld>${tree("")}</p:cSld><p:clrMap accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" bg1=\"lt1\" bg2=\"lt2\" folHlink=\"folHlink\" hlink=\"hlink\" tx1=\"dk1\" tx2=\"dk2\"/><p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst><p:txStyles/></p:sldMaster>",
            "ppt/slideMasters/_rels/slideMaster1.xml.rels" to rels(Triple("rId1", "slideLayout", "../slideLayouts/slideLayout1.xml"), Triple("rId2", "theme", "../theme/theme1.xml")),
            "ppt/slideLayouts/slideLayout1.xml" to "$HEAD<p:sldLayout $ns type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\">${tree("")}</p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>",
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to rels(Triple("rId1", "slideMaster", "../slideMasters/slideMaster1.xml")),
            "ppt/theme/theme1.xml" to theme(),
        )
        s.slides.forEachIndexed { i, text ->
            val paragraphs = text.split('\n').joinToString("") { "<a:p><a:pPr rtl=\"1\"/><a:r><a:rPr lang=\"fa-IR\" sz=\"2400\"/><a:t xml:space=\"preserve\">${xml(it)}</a:t></a:r><a:endParaRPr lang=\"fa-IR\"/></a:p>" }
            val shape = "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"Text\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"457200\" y=\"457200\"/><a:ext cx=\"8229600\" cy=\"5943600\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr><p:txBody><a:bodyPr wrap=\"square\"><a:normAutofit/></a:bodyPr><a:lstStyle/>$paragraphs</p:txBody></p:sp>"
            parts["ppt/slides/slide${i + 1}.xml"] = "$HEAD<p:sld $ns><p:cSld>${tree(shape)}</p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
            parts["ppt/slides/_rels/slide${i + 1}.xml.rels"] = rels(Triple("rId1", "slideLayout", "../slideLayouts/slideLayout1.xml"))
        }
        return zip(parts)
    }
    private fun theme(): String {
        val colors = listOf("dk1" to "000000", "lt1" to "FFFFFF", "dk2" to "1F2937", "lt2" to "F9FAFB", "accent1" to "2563EB", "accent2" to "059669", "accent3" to "D97706", "accent4" to "7C3AED", "accent5" to "DC2626", "accent6" to "0891B2", "hlink" to "0000FF", "folHlink" to "800080")
        val fill = "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>"
        return "$HEAD<a:theme xmlns:a=\"$A\" name=\"Saba\"><a:themeElements><a:clrScheme name=\"Saba\">" + colors.joinToString("") { (n, c) -> "<a:$n><a:srgbClr val=\"$c\"/></a:$n>" } + "</a:clrScheme><a:fontScheme name=\"Native\"><a:majorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"Arial\"/></a:majorFont><a:minorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"Arial\"/></a:minorFont></a:fontScheme><a:fmtScheme name=\"Saba\"><a:fillStyleLst>${fill.repeat(3)}</a:fillStyleLst><a:lnStyleLst>" + (1..3).joinToString("") { "<a:ln w=\"6350\">$fill<a:prstDash val=\"solid\"/></a:ln>" } + "</a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst>${fill.repeat(3)}</a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"
    }
    fun svg(s: DocumentSpec): String {
        validate(s)
        val left = 70.0; val top = 60.0; val plotW = s.width - 100.0; val plotH = s.height - 150.0
        val max = s.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val step = plotW / s.values.size
        fun number(v: Double) = java.lang.String.format(java.util.Locale.ROOT, "%.3f", v)
        return "$HEAD<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${s.width}\" height=\"${s.height}\" viewBox=\"0 0 ${s.width} ${s.height}\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><title>${xml(s.title)}</title><text x=\"${s.width / 2}\" y=\"30\" text-anchor=\"middle\" direction=\"rtl\">${xml(s.title)}</text>" + s.values.indices.joinToString("") { i ->
            val h = plotH * s.values[i] / max; val x = left + i * step
            "<g><title>${xml(s.labels[i])}: ${s.values[i]}</title><rect x=\"${number(x)}\" y=\"${number(top + plotH - h)}\" width=\"${number(step * 0.7)}\" height=\"${number(h)}\" fill=\"#2563eb\"/><text x=\"${number(x + step * 0.35)}\" y=\"${number(top + plotH + 20)}\" text-anchor=\"middle\" direction=\"rtl\" font-size=\"10\">${xml(s.labels[i])}</text></g>"
        } + "</svg>"
    }
}

/** Enforce the output ceiling while encoders write, before buffer growth. */
internal class BoundedDocumentOutput : ByteArrayOutputStream() {
    override fun write(value: Int) {
        require(count < DocumentEncoder.MAX_OUTPUT) { "Output too large" }
        super.write(value)
    }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(length >= 0 && count <= DocumentEncoder.MAX_OUTPUT - length) { "Output too large" }
        super.write(bytes, offset, length)
    }
}
