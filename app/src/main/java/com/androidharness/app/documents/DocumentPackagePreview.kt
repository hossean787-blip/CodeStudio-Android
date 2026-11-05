package com.androidharness.app.documents

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/** Read a bounded, text-only preview from the actual exported Office/ZIP package. */
object DocumentPackagePreview {
    private const val PREVIEW_LIMIT = 12_000
    private const val INPUT_LIMIT = 8 * 1024 * 1024
    private const val WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val SHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private val excelLiteralEscape = Regex("_x005F_(?=x[0-9A-Fa-f]{4}_)")

    fun extract(file: File, format: String): String {
        require(format in setOf("docx", "xlsx", "pptx", "zip")) { "Unsupported package preview" }
        require(file.isFile && file.length() in 1..INPUT_LIMIT.toLong()) { "Invalid package size" }
        ZipFile(file).use { zip ->
            require(zip.size() <= 120) { "Too many package parts" }
            if (format == "zip") return zipSummary(zip)
            val parts = when (format) {
                "docx" -> listOf("word/document.xml")
                "xlsx" -> listOf("xl/worksheets/sheet1.xml")
                else -> (1..32).map { "ppt/slides/slide$it.xml" }.filter { zip.getEntry(it) != null }
            }
            require(parts.isNotEmpty()) { "Missing document content" }
            val summary = BoundedSummary(PREVIEW_LIMIT)
            parts.forEachIndexed { index, name ->
                if (format == "pptx") summary.append("\n\n# ${index + 1}\n")
                val entry = zip.getEntry(name) ?: error("Missing document part")
                require(!entry.isDirectory && entry.size in 1..INPUT_LIMIT.toLong()) { "Invalid document part" }
                zip.getInputStream(entry).use { input -> parseXml(LimitedInput(input, INPUT_LIMIT.toLong()), format, summary) }
            }
            return summary.result()
        }
    }

    private fun zipSummary(zip: ZipFile): String {
        val output = BoundedSummary(PREVIEW_LIMIT)
        val entries = zip.entries()
        var count = 0
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            require(!entry.isDirectory && entry.size in 0..(256 * 1024L)) { "Unsupported archive entry" }
            if (count++ > 0) output.append("\n\n")
            output.append(entry.name + "\n")
            zip.getInputStream(entry).use { input ->
                val source = LimitedInput(input, 256 * 1024L)
                val chars = CharArray(2048)
                InputStreamReader(source, Charsets.UTF_8).use { reader ->
                    while (output.remaining() > 0) {
                        val read = reader.read(chars, 0, minOf(chars.size, output.remaining()))
                        if (read < 0) break
                        output.append(String(chars, 0, read))
                    }
                }
                if (output.remaining() == 0 || source.bytesRead < entry.size) output.markTruncated()
            }
        }
        return output.result()
    }

    private fun parseXml(input: InputStream, format: String, summary: BoundedSummary) {
        val parser = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newSAXParser()
        parser.parse(input, object : DefaultHandler() {
            private var collecting = false
            private val text = StringBuilder()
            private var firstCell = true
            override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
                when {
                    format == "docx" && uri == WORD && localName == "p" -> {
                        if (summary.hasContent()) summary.append("\n")
                    }
                    format == "xlsx" && uri == SHEET && localName == "row" -> {
                        if (summary.hasContent()) summary.append("\n")
                        firstCell = true
                    }
                    format == "xlsx" && uri == SHEET && localName == "c" -> {
                        if (!firstCell) summary.append("\t")
                        firstCell = false
                    }
                    format == "pptx" && uri == DRAWING && localName == "p" -> {
                        if (summary.hasContent()) summary.append("\n")
                    }
                    format == "docx" && uri == WORD && localName == "tab" -> summary.append("\t")
                    format == "docx" && uri == WORD && localName == "br" -> summary.append("\n")
                }
                if (localName == "t" && ((format == "docx" && uri == WORD) ||
                    (format == "xlsx" && uri == SHEET) || (format == "pptx" && uri == DRAWING))) {
                    collecting = true
                    text.setLength(0)
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (collecting && text.length <= PREVIEW_LIMIT) text.append(ch, start, minOf(length, PREVIEW_LIMIT + 1 - text.length))
            }
            override fun endElement(uri: String, localName: String, qName: String) {
                if (collecting && localName == "t") {
                    val value = text.toString()
                    summary.append(if (format == "xlsx") excelLiteralEscape.replace(value, "_") else value)
                    collecting = false
                }
            }
        })
    }

    private class BoundedSummary(private val limit: Int) {
        private val result = StringBuilder()
        private var truncated = false
        fun remaining() = limit - result.length
        fun hasContent() = result.isNotEmpty()
        fun markTruncated() { truncated = true }
        fun append(value: String) {
            val available = remaining()
            if (value.length > available) truncated = true
            if (available > 0) result.append(value, 0, minOf(value.length, available))
        }
        fun result(): String = result.toString().trim() + if (truncated) "\n…" else ""
    }

    private class LimitedInput(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        var bytesRead = 0L
            private set
        override fun read(): Int {
            val value = super.read()
            if (value >= 0 && ++bytesRead > limit) error("Document preview input limit exceeded")
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0 && (bytesRead + read).also { bytesRead = it } > limit) error("Document preview input limit exceeded")
            return read
        }
    }
}
