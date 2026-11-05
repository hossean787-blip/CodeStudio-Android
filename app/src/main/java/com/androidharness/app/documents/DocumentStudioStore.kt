package com.androidharness.app.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Dedicated private directory: never a project/workspace grant. Only our nonce files are touched. */
class DocumentStudioStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.applicationContext.noBackupFilesDir, "document-studio-v1").apply { mkdirs() }
    private val ownName = Regex("studio-[a-f0-9-]{36}\\.bin")
    fun cleanup() { directory.listFiles()?.filter { ownName.matches(it.name) && System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() } }
    fun recover(name: String, hash: String): File {
        require(ownName.matches(name)) { "Missing private document; generate again" }
        val file = File(directory, name)
        require(file.isFile && file.length() in 1..DocumentEncoder.MAX_OUTPUT.toLong() && digest(file) == hash) { "Private document expired or changed; generate again" }
        return file
    }
    suspend fun generate(spec: DocumentSpec): Pair<String, String> {
        val job = coroutineContext
        val bytes = AndroidDocumentRenderer.encode(spec) { job.ensureActive() }
        job.ensureActive()
        val file = File(directory, "studio-${UUID.randomUUID()}.bin")
        try {
            file.outputStream().use { it.write(bytes) }
            job.ensureActive()
            return file.name to digest(file)
        } catch (failure: Throwable) { file.delete(); throw failure }
    }
    fun delete(name: String) { if (ownName.matches(name)) File(directory, name).delete() }
    fun preview(name: String, hash: String, mime: String): Bitmap? {
        val file = recover(name, hash)
        return when (mime) {
            "application/pdf" -> renderOwnedPreview(595, 842) { bitmap ->
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer -> renderer.openPage(0).use { page ->
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } }
                }
            }
            "image/png" -> BitmapFactory.decodeFile(file.path) ?: error("PNG preview failed")
            else -> null
        }
    }
    fun source(name: String, hash: String): String = recover(name, hash).readText(Charsets.UTF_8)
    /** A file-backed text preview, never a claim of full Office layout rendering. */
    fun previewText(name: String, hash: String, mime: String): String {
        val file = recover(name, hash)
        return when (mime) {
            "text/plain", "image/svg+xml" -> {
                val text = file.readText(Charsets.UTF_8)
                text.take(12_000) + if (text.length > 12_000) "\n…" else ""
            }
            DocumentEncoder.mimeTypes.getValue("docx") -> DocumentPackagePreview.extract(file, "docx")
            DocumentEncoder.mimeTypes.getValue("xlsx") -> DocumentPackagePreview.extract(file, "xlsx")
            DocumentEncoder.mimeTypes.getValue("pptx") -> DocumentPackagePreview.extract(file, "pptx")
            DocumentEncoder.mimeTypes.getValue("zip") -> DocumentPackagePreview.extract(file, "zip")
            else -> ""
        }
    }
    /**
     * SAF-selected source is read once into a bounded, disposable private file.
     * Only text and the limited generated OOXML subset are supported. This is
     * an extracted-text draft, NOT in-place or layout-preserving Office editing.
     */
    suspend fun importEditableText(uri: Uri, format: String): String {
        require(format in setOf("txt", "code", "docx", "xlsx", "pptx")) {
            "This file format cannot be imported as an editable text draft"
        }
        val job = coroutineContext
        val temporary = File(directory, "studio-${UUID.randomUUID()}.bin")
        try {
            resolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16_384)
                    var total = 0L
                    while (true) {
                        job.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        require(count > 0) { "No progress reading selected document" }
                        total += count
                        require(total <= DocumentEncoder.MAX_OUTPUT) { "Selected document exceeds the 8 MiB limit" }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Selected document could not be read")
            job.ensureActive()
            require(temporary.length() in 1..DocumentEncoder.MAX_OUTPUT.toLong()) { "Empty or oversized document" }
            val draft = when (format) {
                "txt", "code" -> {
                    val decoder = Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                    decoder.decode(ByteBuffer.wrap(temporary.readBytes())).toString()
                }
                else -> DocumentPackagePreview.extractEditable(temporary, format)
            }
            require(draft.isNotBlank() && draft.length <= 100_000) { "Empty or excessive editable content" }
            require(!draft.endsWith("\n…")) { "Document text preview was truncated; edit rejected" }
            DocumentEncoder.validText(draft)
            return draft
        } finally {
            // Never retain a second copy of a user-selected external document.
            temporary.delete()
        }
    }

    suspend fun save(uri: Uri, name: String, hash: String) {
        val file = recover(name, hash)
        val job = coroutineContext
        job.ensureActive()
        resolver.openOutputStream(uri, "w")?.use { output -> file.inputStream().use { input ->
            val buffer = ByteArray(16384)
            while (true) { job.ensureActive(); val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
        } } ?: error("Provider did not open destination")
        job.ensureActive()
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16384)
            while (true) { job.ensureActive(); val count = input.read(buffer); if (count < 0) break; size += count; require(size <= DocumentEncoder.MAX_OUTPUT); digest.update(buffer, 0, count) }
        } ?: error("Provider did not reopen destination")
        require(size == file.length() && hex(digest.digest()) == hash) { "Saved bytes did not verify" }
    }
    private fun digest(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(16384)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        hex(digest.digest())
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}

/** Owner exists before dispatch, so a cancelled withContext return cannot orphan native pixels. */
internal suspend fun loadOwnedDocumentPreview(create: () -> Bitmap?, adopt: (Bitmap?) -> Unit) {
    var pending: Bitmap? = null
    var failure: Throwable? = null
    try {
        withContext(Dispatchers.IO) { pending = create() }
        adopt(pending)
        pending = null // synchronous adoption completed; UI owner now controls disposal
    } catch (original: Throwable) {
        failure = original
        throw original
    } finally {
        recyclePreview(pending, failure)
    }
}

/** Covers render failures and exceptions while closing the renderer/page/descriptors. */
internal fun renderOwnedPreview(width: Int, height: Int, render: (Bitmap) -> Unit): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        render(bitmap)
        return bitmap
    } catch (original: Throwable) {
        recyclePreview(bitmap, original)
        throw original
    }
}

/** UI ownership is registered immediately, rather than waiting for the next recomposition effect. */
internal class DocumentPreviewOwner : AutoCloseable {
    private var bitmap: Bitmap? = null
    fun replace(value: Bitmap?) {
        if (bitmap === value) return
        val previous = bitmap
        bitmap = value
        recyclePreview(previous, null)
    }
    override fun close() = replace(null)
}

private fun recyclePreview(bitmap: Bitmap?, original: Throwable?) {
    try { bitmap?.recycle() }
    catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup }
}
