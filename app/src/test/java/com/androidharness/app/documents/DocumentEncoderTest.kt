package com.androidharness.app.documents

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocumentEncoderTest {
    private val persian = "گزارش فارسی — سلام دنیا & <آزمایش>"
    private fun parts(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                assertFalse(e.isDirectory)
                assertNull("Duplicate package part", entries.put(e.name, z.readBytes()))
            }
        }
        return entries
    }
    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    private fun invalid(block: () -> Unit) {
        try { block(); fail("Input should be rejected") } catch (_: IllegalArgumentException) { }
    }
    @Test fun officePackagesHaveParseablePartsResolvableRelationshipsAndUnicode() {
        val specs = listOf(
            DocumentSpec("docx", persian, "$persian\nSecond line"),
            DocumentSpec("xlsx", rows = listOf(listOf(persian, "=SUM(A1:A2)", "+1", "-1", "@command", "00123"))),
            DocumentSpec("pptx", slides = listOf(persian, "Second slide")),
        )
        specs.forEach { spec ->
            val bytes = DocumentEncoder.encode(spec)
            assertArrayEquals("Deterministic package", bytes, DocumentEncoder.encode(spec))
            val p = parts(bytes)
            assertTrue(p.containsKey("[Content_Types].xml")); assertTrue(p.containsKey("_rels/.rels"))
            p.forEach { (name, content) ->
                val d = xml(content)
                if (name.endsWith(".rels")) {
                    val rels = d.getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationship")
                    val base = if (name == "_rels/.rels") "" else name.substringBefore("_rels/")
                    for (i in 0 until rels.length) {
                        val e = rels.item(i) as org.w3c.dom.Element
                        assertFalse(e.hasAttribute("TargetMode"))
                        val target = java.nio.file.Paths.get(base).resolve(e.getAttribute("Target")).normalize().toString().replace('\\', '/')
                        assertTrue("Missing $target from $name", target in p)
                    }
                }
            }
            assertTrue(p.values.any { xml(it).documentElement.textContent.contains(persian) })
            val dir = File("build/outputs/issue8/documents").apply { mkdirs() }
            File(dir, "fixture.${spec.format}").writeBytes(bytes)
        }
    }
    @Test fun spreadsheetFormulaCharactersRemainInlineStringsWithoutFormulaElements() {
        val values = listOf("=1+1", "+SUM(A1)", "-2", "@x", "00123", persian)
        val p = parts(DocumentEncoder.encode(DocumentSpec("xlsx", rows = listOf(values))))
        val sheet = xml(p.getValue("xl/worksheets/sheet1.xml"))
        val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        assertEquals(0, sheet.getElementsByTagNameNS(ns, "f").length)
        val cells = sheet.getElementsByTagNameNS(ns, "c")
        assertEquals(values.size, cells.length)
        values.forEachIndexed { i, v ->
            assertEquals("inlineStr", (cells.item(i) as org.w3c.dom.Element).getAttribute("t"))
            assertEquals(v, cells.item(i).textContent)
        }
    }
    @Test fun archiveHasOnlyExplicitEntriesAndExactUtf8() {
        val input = listOf(ArchiveText("notes/فارسی.txt", persian), ArchiveText("src/main.kt", "fun main() = Unit\n"))
        val p = parts(DocumentEncoder.encode(DocumentSpec("zip", entries = input)))
        assertEquals(input.map { it.name }.toSet(), p.keys)
        input.forEach { assertEquals(it.text, p.getValue(it.name).toString(Charsets.UTF_8)) }
    }
    @Test fun unsafeArchiveNamesDuplicatesAndFileDirectoryCollisionsAreRejected() {
        listOf("../secret", "/absolute", "C:/file", "x\\y", "a/../../b", "a//b", "a/./b", "a\u0000b", "a\nb").forEach { name ->
            invalid { DocumentEncoder.encode(DocumentSpec("zip", entries = listOf(ArchiveText(name, "x")))) }
        }
        invalid { DocumentEncoder.encode(DocumentSpec("zip", entries = listOf(ArchiveText("a", "1"), ArchiveText("a", "2")))) }
        invalid { DocumentEncoder.encode(DocumentSpec("zip", entries = listOf(ArchiveText("a", "1"), ArchiveText("a/b", "2")))) }
    }
    @Test fun unicodeControlsAndInputBoundsAreValidatedBeforeEncoding() {
        listOf("bad\u0000", "bad\u0001", "bad\uD800", "bad\uDC00", "bad\uFFFF").forEach { invalid { DocumentEncoder.encode(DocumentSpec("docx", text = it)) } }
        invalid { DocumentEncoder.encode(DocumentSpec("txt", text = "x".repeat(100001))) }
        invalid { DocumentEncoder.encode(DocumentSpec("xlsx", rows = List(1001) { listOf("x") })) }
        invalid { DocumentEncoder.encode(DocumentSpec("xlsx", rows = listOf(List(51) { "x" }))) }
        invalid { DocumentEncoder.encode(DocumentSpec("pptx", slides = List(33) { "x" })) }
        invalid { DocumentEncoder.encode(DocumentSpec("zip", entries = List(101) { ArchiveText("$it", "x") })) }
        assertArrayEquals(persian.toByteArray(Charsets.UTF_8), DocumentEncoder.encode(DocumentSpec("txt", text = persian)))
    }
    @Test fun outputCeilingPreventsBufferGrowthBeyondLimit() {
        val out = BoundedDocumentOutput()
        out.write(ByteArray(DocumentEncoder.MAX_OUTPUT), 0, DocumentEncoder.MAX_OUTPUT)
        invalid { out.write(1) }
        invalid { out.write(byteArrayOf(1, 2), 0, 2) }
        assertEquals(DocumentEncoder.MAX_OUTPUT, out.size())
    }
    @Test fun chartRejectsInvalidDataAndProducesEditableEscapedSvg() {
        val s = DocumentSpec("svg", title = persian, labels = listOf("الف & <ب>", "ج"), values = listOf(0.0, 10.0))
        val d = xml(DocumentEncoder.encode(s))
        assertEquals("svg", d.documentElement.localName)
        assertEquals(2, d.getElementsByTagNameNS("http://www.w3.org/2000/svg", "g").length)
        assertTrue(d.documentElement.textContent.contains(persian))
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 1e13).forEach { invalid { DocumentEncoder.encode(s.copy(values = listOf(it, 1.0))) } }
        invalid { DocumentEncoder.encode(s.copy(width = 0)) }
        invalid { DocumentEncoder.encode(s.copy(values = emptyList())) }
    }
}
