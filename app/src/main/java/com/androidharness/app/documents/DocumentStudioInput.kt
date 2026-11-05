package com.androidharness.app.documents

/** Bounded, literal input only. No formulas, URLs, filesystem reads or script execution. */
object DocumentStudioInput {
    fun fileName(title: String, format: String): String {
        require(format in DocumentEncoder.mimeTypes)
        require(title.isNotBlank() && title.length <= 120) { "Use a title of 1–120 characters" }
        require(title.none { it == '/' || it == '\\' || it == ':' || it.code < 32 || it.code == 127 }) { "Title must be a simple filename" }
        require(title != "." && title != "..")
        DocumentEncoder.validText(title)
        return "$title.${if (format == "code") "txt" else format}"
    }
    fun parse(format: String, title: String, input: String): DocumentSpec {
        fileName(title, format)
        require(input.isNotBlank() && input.length <= 100_000) { "Enter 1–100000 characters" }
        val normalized = input.replace("\r\n", "\n").replace('\r', '\n')
        val spec = when (format) {
            "xlsx" -> DocumentSpec(format, title, rows = normalized.split('\n').map { it.split('\t') })
            "pptx" -> DocumentSpec(format, title, slides = normalized.split("\n---\n").also { require(it.none(String::isBlank)) { "Empty slide" } })
            "zip" -> DocumentSpec(format, title, entries = listOf(ArchiveText("content.txt", input)))
            "svg", "png" -> {
                val pairs = normalized.split('\n').map { line ->
                    val split = line.lastIndexOf(':')
                    require(split > 0) { "Each chart line needs label:number" }
                    val label = line.substring(0, split).trim()
                    val number = line.substring(split + 1).trim().map { c -> when (c) {
                        in '۰'..'۹' -> '0' + (c - '۰')
                        in '٠'..'٩' -> '0' + (c - '٠')
                        '٫' -> '.'
                        else -> c
                    } }.joinToString("")
                    require(label.isNotBlank()) { "Empty chart label" }
                    label to (number.toDoubleOrNull() ?: error("Invalid chart number"))
                }
                DocumentSpec(format, title, labels = pairs.map { it.first }, values = pairs.map { it.second })
            }
            else -> DocumentSpec(format, title, text = input)
        }
        DocumentEncoder.validate(spec)
        return spec
    }
}
