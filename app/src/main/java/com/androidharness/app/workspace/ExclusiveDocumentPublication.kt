package com.androidharness.app.workspace

import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Complete bytes become visible in one exclusive hard-link operation; never fall back to replacement. */
internal object ExclusiveDocumentPublication {
    private const val MAX_BYTES = 16 * 1024 * 1024

    fun publish(root: File, capturedRoot: Path, relativePath: String, bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "Document exceeds the 16 MiB publication limit" }
        val parts = relativePath.split('/')
        require(relativePath.isNotBlank() && !relativePath.startsWith('/') && '\\' !in relativePath &&
            parts.none { it.isBlank() || it == "." || it == ".." } && relativePath.none { it.isISOControl() }) {
            "Use a non-empty workspace-relative document path without traversal"
        }
        require(root.canonicalFile.toPath() == capturedRoot && !Files.isSymbolicLink(root.toPath())) {
            "Workspace location changed; no document was written"
        }
        var parent = capturedRoot
        for (part in parts.dropLast(1)) {
            parent = parent.resolve(part)
            if (!Files.exists(parent, NOFOLLOW_LINKS)) {
                try { Files.createDirectory(parent) } catch (_: java.nio.file.FileAlreadyExistsException) { }
            }
            require(Files.isDirectory(parent, NOFOLLOW_LINKS) && !Files.isSymbolicLink(parent)) {
                "Document parent must be a real directory inside this workspace"
            }
        }
        require(parent.toRealPath() == parent && parent.startsWith(capturedRoot)) { "Document path escaped the workspace" }
        val target = parent.resolve(parts.last())
        var temporary: Path? = null
        try {
            temporary = Files.createTempFile(parent, ".saba-document-", ".tmp")
            FileOutputStream(temporary.toFile()).use { output ->
                output.write(bytes); output.flush(); output.fd.sync()
            }
            // link() atomically fails for existing regular files, directories and
            // even dangling links. Unlike rename/ATOMIC_MOVE it never replaces.
            Files.createLink(target, temporary)
            return relativePath
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            throw IOException("Document already exists: $relativePath. Choose a new filename; nothing was replaced.")
        } catch (_: java.io.IOException) {
            throw IOException("This workspace could not publish a new document atomically. Use an app-private writable project; nothing was replaced.")
        } catch (_: UnsupportedOperationException) {
            throw IOException("This workspace does not support exclusive atomic document publication. Use an app-private project.")
        } finally {
            temporary?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }
}
