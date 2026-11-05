package com.androidharness.app.documents

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
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
    @Test fun cleanupLeavesUnownedNamesUntouched() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DocumentStudioStore(context)
        val unrelated = File(context.noBackupFilesDir, "document-studio-v1/leave-me.txt")
        try { unrelated.writeText("synthetic"); unrelated.setLastModified(1); store.cleanup(); assertTrue(unrelated.exists()) }
        finally { unrelated.delete() }
    }
}
