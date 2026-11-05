package com.androidharness.app.tools

import com.androidharness.app.documents.AndroidDocumentRenderer
import com.androidharness.app.documents.ArchiveText
import com.androidharness.app.documents.DocumentEncoder
import com.androidharness.app.documents.DocumentExportSink
import com.androidharness.app.documents.DocumentSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Writes a real project file. Separate from sandboxed conversation create_artifact. */
class CreateDocumentTool : Tool {
    override val name = "create_document"
    override val description = "Export a new native PDF, DOCX, XLSX, PPTX, ZIP, UTF-8 text/code, SVG bar chart or PNG bar chart in the selected writable project. Existing paths are never replaced. No external resources, formulas, macros or filesystem archive inputs. Returns actual path, MIME and byte count."
    override val isReadOnly = false
    override val parametersSchema = Schema.obj(mapOf(
        "path" to Schema.string("Exact new workspace-relative output file path; no overwrite."),
        "format" to buildJsonObject { put("type", "string"); putJsonArray("enum") { DocumentEncoder.mimeTypes.keys.forEach { add(it) } } },
        "title" to Schema.string("Optional title, at most 200 characters."),
        "text" to Schema.string("PDF/DOCX/text/code body; at most 100000 characters."),
        "rows" to Schema.array(Schema.array(Schema.string("Literal cell text, including leading formula characters."), "Row cells; at most 50."), "XLSX rows; at most 1000."),
        "slides" to Schema.array(Schema.string("Slide body text; at most 4000 characters."), "PPTX slide texts; 1 to 32."),
        "entries" to Schema.array(Schema.obj(mapOf("name" to Schema.string("Safe relative ZIP entry name."), "text" to Schema.string("UTF-8 entry content.")), listOf("name", "text")), "ZIP explicit text entries; 1 to 100; no implicit file reads."),
        "labels" to Schema.array(Schema.string("Chart category; at most 80 characters."), "Chart labels; at most 32."),
        "values" to Schema.array(buildJsonObject { put("type", "number"); put("minimum", 0); put("maximum", 1e12) }, "Matching finite nonnegative chart values."),
        "width" to Schema.integer("Chart pixel width: 320 to 1600; default 800."),
        "height" to Schema.integer("Chart pixel height: 240 to 1200; default 600."),
    ), listOf("path", "format"))

    // Availability must also work when routing inspects the original binding before wrapping grants.
    override fun isAvailable(ctx: ToolContext) = ctx.workspace is DocumentExportSink

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult = withContext(Dispatchers.IO) {
        val sink = ctx.workspace as? DocumentExportSink
            ?: throw ToolFailure("This project provider cannot safely create exclusive atomic exports. Choose a supported writable project; no file was written.")
        val spec: DocumentSpec
        val path: String
        try {
            path = DocumentEncoder.path(string(args, "path", required = true))
            spec = parse(args)
            val extension = path.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            require(spec.format == "code" || extension == spec.format) { "Path extension must match format" }
            if (spec.format == "code") require(extension in setOf("kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "rs", "go", "sh", "css", "html", "json", "xml", "yaml", "yml", "md", "txt", "sql")) { "Unsupported code extension" }
            DocumentEncoder.validate(spec)
        } catch (e: IllegalArgumentException) { throw ToolFailure(e.message ?: "Invalid document input") }
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()
        val bytes = try { AndroidDocumentRenderer.encode(spec) { coroutine.ensureActive() } }
            catch (e: IllegalArgumentException) { throw ToolFailure(e.message ?: "Cannot encode document") }
        coroutine.ensureActive()
        val actual = sink.publishDocumentExclusive(path, bytes)
        ToolResult(true, buildJsonObject {
            put("path", actual); put("format", spec.format); put("mime", DocumentEncoder.mimeTypes.getValue(spec.format)); put("bytes", bytes.size)
            put("status", "Created new project document")
        }.toString())
    }

    companion object {
        private fun string(obj: JsonObject, key: String, required: Boolean = false): String {
            val v = obj[key] ?: return if (!required) "" else throw IllegalArgumentException("Missing $key")
            return (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("$key must be a string")
        }
        private fun array(obj: JsonObject, key: String, max: Int): JsonArray {
            val a = obj[key]?.let { it as? JsonArray ?: throw IllegalArgumentException("$key must be an array") } ?: JsonArray(emptyList())
            require(a.size <= max) { "$key exceeds item limit" }; return a
        }
        private fun strings(a: JsonArray): List<String> = a.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: throw IllegalArgumentException("Expected string array") }
        private fun integer(obj: JsonObject, key: String, fallback: Int): Int = obj[key]?.let {
            val v = it as? JsonPrimitive
            require(v != null && !v.isString) { "$key must be an integer" }
            v.intOrNull ?: throw IllegalArgumentException("$key must be an integer")
        } ?: fallback
        internal fun parse(args: JsonObject): DocumentSpec = DocumentSpec(
            format = string(args, "format", true), title = string(args, "title"), text = string(args, "text"),
            rows = array(args, "rows", 1000).map { row ->
                val a = row as? JsonArray ?: throw IllegalArgumentException("Each row must be an array")
                require(a.size <= 50) { "Too many columns" }; strings(a)
            },
            slides = strings(array(args, "slides", 32)),
            entries = array(args, "entries", 100).map { item ->
                val o = item as? JsonObject ?: throw IllegalArgumentException("Invalid archive entry")
                ArchiveText(string(o, "name", true), string(o, "text", true))
            },
            labels = strings(array(args, "labels", 32)),
            values = array(args, "values", 32).map {
                val v = it as? JsonPrimitive
                require(v != null && !v.isString) { "Chart values must be numbers" }
                v.doubleOrNull ?: throw IllegalArgumentException("Invalid chart number")
            },
            width = integer(args, "width", 800), height = integer(args, "height", 600),
        )
    }
}
