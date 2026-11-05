package com.androidharness.app.ui.library

import java.security.MessageDigest

/** Stable per-project private index name; never use raw project IDs as paths. */
internal object LibraryScope {
    const val LEGACY_FILE_NAME = "saba_private_sources.json"

    fun fileName(projectId: String): String {
        require(projectId.isNotBlank() && projectId.length <= 256) { "A valid project scope is required" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(projectId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "saba_private_sources_${digest}.json"
    }
}
