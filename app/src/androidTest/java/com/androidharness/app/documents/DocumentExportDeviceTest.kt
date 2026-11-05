package com.androidharness.app.documents

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/** Produces real native fixtures for independent desktop readers after CI pulls artifacts. */
@RunWith(AndroidJUnit4::class)
class DocumentExportDeviceTest {
    @Test fun nativeRenderCancellationAndPageBoundsFailBeforePublication() {
        var checks = 0
        try {
            AndroidDocumentRenderer.encode(DocumentSpec("png", labels = listOf("a", "b", "c"), values = listOf(1.0, 2.0, 3.0))) {
                checks++
                if (checks == 3) throw kotlinx.coroutines.CancellationException("cancelled")
            }
            fail("Cancellation must escape the renderer")
        } catch (_: kotlinx.coroutines.CancellationException) { assertEquals(3, checks) }
        try {
            AndroidDocumentRenderer.encode(DocumentSpec("pdf", text = "x\n".repeat(7000)))
            fail("Oversize pagination must fail rather than truncate")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun pdfCancellationAfterPageStartPreservesOriginalFailure() {
        val cancelled = kotlinx.coroutines.CancellationException("cancel while page is open")
        var checks = 0
        try {
            AndroidDocumentRenderer.encode(DocumentSpec("pdf", text = "سلام")) {
                // Initial, pagination, pre-page, then the check immediately after startPage.
                if (++checks == 4) throw cancelled
            }
            fail("Cancellation must escape page/document cleanup")
        } catch (failure: kotlinx.coroutines.CancellationException) {
            assertSame(cancelled, failure)
            assertTrue("Native cleanup should succeed", failure.suppressed.isEmpty())
        }
        assertTrue(AndroidDocumentRenderer.encode(DocumentSpec("pdf", text = "سلام")).isNotEmpty())
    }

    @Test fun generateAndReopenNativeDocuments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "issue8/documents").apply { mkdirs() }
        val fa = "گزارش فارسی — سلام دنیا & <آزمایش>"
        val body = (1..240).joinToString("\n") { "$it: $fa" }
        val specs = listOf(
            DocumentSpec("pdf", fa, body),
            DocumentSpec("docx", fa, body),
            DocumentSpec("xlsx", rows = listOf(listOf("عنوان", "مقدار"), listOf(fa, "=1+1"), listOf("literal", "+SUM(A1)"), listOf("عدد", "00123"))),
            DocumentSpec("pptx", title = fa, slides = listOf(fa, "اسلاید دوم\nSecond slide")),
            DocumentSpec("zip", entries = listOf(ArchiveText("notes/فارسی.txt", fa), ArchiveText("src/main.kt", "fun main() = Unit\n"))),
            DocumentSpec("txt", text = fa), DocumentSpec("code", text = "// $fa\nfun main() = Unit\n"),
            DocumentSpec("svg", title = "فروش", labels = listOf("الف", "ب", "ج"), values = listOf(3.0, 7.0, 5.0)),
            DocumentSpec("png", title = "فروش", labels = listOf("الف", "ب", "ج"), values = listOf(3.0, 7.0, 5.0)),
        )
        specs.forEach { s ->
            val ext = if (s.format == "code") "kt" else s.format
            val file = File(dir, "fixture.$ext")
            file.writeBytes(AndroidDocumentRenderer.encode(s))
            assertTrue(file.length() > 0)
            when (s.format) {
                "pdf" -> ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        assertTrue("Must paginate instead of truncating", renderer.pageCount > 1)
                        for (pageNumber in 0 until renderer.pageCount) renderer.openPage(pageNumber).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pixels = IntArray(bitmap.width * bitmap.height)
                                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                                assertTrue("Page $pageNumber must have rendered ink", pixels.any { (it and 0x00FFFFFF) != 0x00FFFFFF && (it ushr 24) != 0 })
                                if (pageNumber == 0 || pageNumber == renderer.pageCount - 1) {
                                    File(dir, "pdf-page-${pageNumber + 1}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                }
                            } finally { bitmap.recycle() }
                        }
                    }
                }
                "png" -> {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    assertNotNull(bitmap); assertEquals(800, bitmap.width); assertEquals(600, bitmap.height)
                    assertTrue(bitmap.getPixel(100, 450) != android.graphics.Color.WHITE)
                    bitmap.recycle()
                }
                "docx", "xlsx", "pptx", "zip" -> ZipFile(file).use { z -> assertTrue(z.entries().hasMoreElements()) }
                "txt", "code" -> assertEquals(s.text, file.readText(Charsets.UTF_8))
            }
        }
        File(dir, "spreadsheet-literals.xlsx").writeBytes(AndroidDocumentRenderer.encode(DocumentSpec("xlsx", rows = listOf(listOf("_x0041_", "_x000D_", "_x005F_", "_x0041_x0042_", "_x005F_x0041_", fa, "=1+1", "+SUM(A1)", "00123")))))
        File(dir, "spreadsheet-boundary.xlsx").writeBytes(AndroidDocumentRenderer.encode(DocumentSpec("xlsx", rows = listOf(listOf("ی".repeat(32767)), listOf("\uD83D\uDE00".repeat(16383) + "ی")))))
        File(dir, "evidence.txt").writeText("Generated native PDF/PNG; reopened every PDF page with PdfRenderer and PNG with BitmapFactory. Office and ZIP reopened via ZipFile; independent Office readers still required.\n")
    }
}
