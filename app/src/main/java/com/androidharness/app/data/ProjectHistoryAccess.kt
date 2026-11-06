package com.androidharness.app.data

/** Conversation recall never crosses assigned project boundaries implicitly. */
internal object ProjectHistoryAccess {
    fun sameProject(sourceProjectId: String?, targetProjectId: String?): Boolean =
        !sourceProjectId.isNullOrBlank() && sourceProjectId == targetProjectId
}
