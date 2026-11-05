package com.androidharness.app.documents

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native Android generation and preview, independent of navigation/network. */
@RunWith(AndroidJUnit4::class)
class DocumentStudioStoreDeviceTest {
    @Test fun pdfAndPngGeneratePreviewAndRecoverOnlyMatchingPrivateBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DocumentStudioStore(context)
        for ((format, input) in listOf("pdf" to "سلام دنیا\nSynthetic document", "png" to "الف:۱۲\nب:٣")) {
            val generated = store.generate(DocumentStudioInput.parse(format, "Synthetic", input))
            try {
                val file = store.recover(generated.first, generated.second)
                assertEquals(context.noBackupFilesDir.canonicalPath + "/document-studio-v1", file.parentFile!!.canonicalPath)
                val bitmap = store.preview(generated.first, generated.second, DocumentEncoder.mimeTypes.getValue(format))!!
                assertTrue(bitmap.width > 0 && bitmap.height > 0); bitmap.recycle()
                file.appendText("tampered")
                assertTrue(runCatching { store.recover(generated.first, generated.second) }.isFailure)
            } finally { store.delete(generated.first) }
        }
        assertTrue(runCatching { store.recover("../outside", "hash") }.isFailure)
        assertTrue(runCatching { store.recover("studio-00000000-0000-0000-0000-000000000000.bin", "hash") }.isFailure)
    }
    @Test fun cancelledPreviewHandoffRecyclesPixelsWithoutAdoption() = runBlocking {
        val pending = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val handoffJob = Job()
        val cancellation = CancellationException("synthetic preview handoff cancellation")
        var adopted = false
        val caught = try {
            withContext(handoffJob) {
                loadOwnedDocumentPreview(create = {
                    // Allocation completes on IO, but the return to its caller is cancelled.
                    handoffJob.cancel(cancellation)
                    pending
                }, adopt = { adopted = true })
            }
            null
        } catch (failure: CancellationException) { failure }
        assertNotNull(caught)
        assertEquals(cancellation.message, caught!!.message)
        assertFalse(adopted)
        assertTrue(pending.isRecycled)
    }
    @Test fun renderFailureAndCancellationRecycleAndPreserveOriginalError() {
        for (original in listOf(IllegalStateException("synthetic render failure"), CancellationException("synthetic render cancellation"))) {
            var allocated: Bitmap? = null
            val caught = try {
                renderOwnedPreview(8, 8) { bitmap -> allocated = bitmap; throw original }
                null
            } catch (failure: Throwable) { failure }
            assertSame(original, caught)
            assertTrue(allocated!!.isRecycled)
        }
    }
    @Test fun adoptedPixelsRemainOwnedUntilReplacementOrImmediateDisposal() = runBlocking {
        val owner = DocumentPreviewOwner()
        val first = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            loadOwnedDocumentPreview(create = { first }, adopt = owner::replace)
            assertFalse(first.isRecycled)
            loadOwnedDocumentPreview(create = { second }, adopt = owner::replace)
            assertTrue(first.isRecycled)
            assertFalse(second.isRecycled)
            // No recomposition is required for the UI disposal owner to know about second.
            owner.close()
            assertTrue(second.isRecycled)
        } finally { owner.close(); if (!first.isRecycled) first.recycle(); if (!second.isRecycled) second.recycle() }
    }
    @Test fun failedAdoptionRecyclesAndPreservesOriginalError() = runBlocking {
        val pending = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val original = IllegalArgumentException("synthetic adoption failure")
        val caught = try {
            loadOwnedDocumentPreview(create = { pending }, adopt = { throw original })
            null
        } catch (failure: Throwable) { failure }
        assertSame(original, caught)
        assertTrue(pending.isRecycled)
    }
    @Test fun cleanupLeavesUnownedNamesUntouched() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DocumentStudioStore(context)
        val unrelated = File(context.noBackupFilesDir, "document-studio-v1/leave-me.txt")
        try { unrelated.writeText("synthetic"); unrelated.setLastModified(1); store.cleanup(); assertTrue(unrelated.exists()) }
        finally { unrelated.delete() }
    }
    @Test fun realOfficeAndZipExportsArePreviewedFromVerifiedPrivateBytes() = runBlocking {
        val store = DocumentStudioStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val content = "گزارش فارسی صبا"
        val cases = listOf(
            DocumentSpec("docx", text = content),
            DocumentSpec("xlsx", rows = listOf(listOf(content, "=42"))),
            DocumentSpec("pptx", slides = listOf(content)),
            DocumentSpec("zip", entries = listOf(ArchiveText("note.txt", content))),
        )
        cases.forEach { spec ->
            val (name, hash) = store.generate(spec)
            try {
                val preview = store.previewText(name, hash, DocumentEncoder.mimeTypes.getValue(spec.format))
                assertTrue("Missing genuine text in ${spec.format}", preview.contains(content))
                assertTrue("Missing actual stored bytes", store.recover(name, hash).length() > 0)
                assertTrue(runCatching { store.previewText(name, "bad-hash", DocumentEncoder.mimeTypes.getValue(spec.format)) }.isFailure)
            } finally { store.delete(name) }
        }
    }

}
