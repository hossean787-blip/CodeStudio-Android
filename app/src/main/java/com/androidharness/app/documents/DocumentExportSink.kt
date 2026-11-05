package com.androidharness.app.documents

/**
 * Optional capability of a WorkspaceFs (including grant-preserving wrappers).
 * Publish fully encoded bytes to the exact workspace-relative path, atomically and
 * exclusively: existing files, directories, links and concurrent winners must cause
 * failure, never replacement. On failure/cancellation leave no partial destination.
 * Implementations must enforce containment, writable grant, bounds and safe parents.
 * A provider without these guarantees must not implement this capability.
 * The caller invokes this on Dispatchers.IO after validation and cancellation checks.
 * Publication is one synchronous commit point; do not throw cancellation after commit.
 */
interface DocumentExportSink {
    fun publishDocumentExclusive(relativePath: String, bytes: ByteArray): String
}
