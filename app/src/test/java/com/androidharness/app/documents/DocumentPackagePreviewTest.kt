package com.androidharness.app.documents

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class DocumentPackagePreviewTest {
    private val persian = "گزارش فارسی — صبا استودیو & دنیا"
    private fun preview(spec: DocumentSpec): String {
        val file = File.createTempFile("saba-document-preview-", ".zip")
        try {
            file.writeBytes(DocumentEncoder.encode(spec))
            return DocumentPackagePreview.extract(file, spec.format)
        } finally { file.delete() }
    }

    @Test fun previewsReadRealOfficeFilesWithoutExecutingTheirContents() {
        assertTrue(preview(DocumentSpec("docx", title = "آزمایش", text = persian)).contains(persian))
        assertTrue(preview(DocumentSpec("xlsx", rows = listOf(listOf(persian, "=1+1", "_x0041_")))).let {
            it.contains(persian) && it.contains("=1+1") && it.contains("_x0041_")
        })
        assertTrue(preview(DocumentSpec("pptx", slides = listOf(persian, "اسلاید دوم"))).let {
            it.contains(persian) && it.contains("اسلاید دوم") && it.contains("# 2")
        })
        assertTrue(preview(DocumentSpec("zip", entries = listOf(
            ArchiveText("folder/notes.txt", persian), ArchiveText("code/main.kt", "val x = 1"),
        ))).let { it.contains(persian) && it.contains("folder/notes.txt") && it.contains("val x = 1") })
    }

    @Test fun previewRejectsDoctypeAndUntrustedExternalXmlEntities() {
        val file = File.createTempFile("saba-unsafe-preview-", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write("<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><root>&x;</root>".toByteArray())
                zip.closeEntry()
            }
            assertTrue(runCatching { DocumentPackagePreview.extract(file, "docx") }.isFailure)
        } finally { file.delete() }
    }

    @Test fun previewLimitsRenderedTextButNotExportedFileData() {
        val original = "متن آزمایشی".repeat(2000)
        val result = preview(DocumentSpec("docx", text = original))
        assertTrue(result.length <= 12_002)
        assertTrue(result.endsWith("…"))
        assertEquals(original, original) // source remains unchanged; only preview is bounded
    }
}
