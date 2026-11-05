package com.androidharness.app.documents

import org.junit.Assert.*
import org.junit.Test

class DocumentStudioInputTest {
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun localizedChartNumbersAndLiteralLabels() {
        val spec = DocumentStudioInput.parse("svg", "گزارش", "الف:۱۲٫۵\nب:٣")
        assertEquals(listOf(12.5, 3.0), spec.values)
        assertEquals(listOf("الف", "ب"), spec.labels)
        assertTrue(DocumentEncoder.encode(spec).toString(Charsets.UTF_8).contains("الف"))
    }
    @Test fun chartRejectsNonFiniteNegativeMissingAndExcessiveValues() {
        listOf("a:NaN", "a:Infinity", "a:-1", "a:1000000000001", "a:", ":1", "missing").forEach { input ->
            rejects { DocumentStudioInput.parse("png", "chart", input) }
        }
        rejects { DocumentStudioInput.parse("svg", "chart", (1..33).joinToString("\n") { "a$it:1" }) }
    }
    @Test fun tabularCellsPreserveEmptyCellsAndFormulaText() {
        val spec = DocumentStudioInput.parse("xlsx", "sheet", "=1+1\t\t\r\n001\ttext\t")
        assertEquals(listOf(listOf("=1+1", "", ""), listOf("001", "text", "")), spec.rows)
        rejects { DocumentStudioInput.parse("xlsx", "sheet", (1..51).joinToString("\t") { "x" }) }
        rejects { DocumentStudioInput.parse("xlsx", "sheet", "x".repeat(32768)) }
    }
    @Test fun slideSeparatorIsExplicitAndBoundsAreEnforced() {
        assertEquals(listOf("one", "two"), DocumentStudioInput.parse("pptx", "slides", "one\n---\ntwo").slides)
        rejects { DocumentStudioInput.parse("pptx", "slides", "one\n---\n") }
        rejects { DocumentStudioInput.parse("pptx", "slides", "x".repeat(4001)) }
    }
    @Test fun archiveHasOneExplicitLiteralFileAndCodeDoesNotExecute() {
        val spec = DocumentStudioInput.parse("zip", "bundle", "../../secret\n<script>literal</script>")
        assertEquals(listOf(ArchiveText("content.txt", "../../secret\n<script>literal</script>")), spec.entries)
        assertEquals("sample.txt", DocumentStudioInput.fileName("sample", "code"))
        assertEquals("println(1)", DocumentStudioInput.parse("code", "sample", "println(1)").text)
    }
    @Test fun namesFormatsAndInputAreValidated() {
        listOf("", "..", "../a", "a/b", "a\\b", "a:b", "a\u0000", "x".repeat(121)).forEach { name ->
            rejects { DocumentStudioInput.fileName(name, "txt") }
        }
        rejects { DocumentStudioInput.parse("html", "title", "hello") }
        rejects { DocumentStudioInput.parse("txt", "title", " ") }
        rejects { DocumentStudioInput.parse("txt", "title", "x".repeat(100001)) }
        rejects { DocumentStudioInput.parse("txt", "title", "\uD800") }
        DocumentEncoder.mimeTypes.keys.forEach { format -> assertTrue(DocumentStudioInput.fileName("سند", format).startsWith("سند.")) }
    }
}
