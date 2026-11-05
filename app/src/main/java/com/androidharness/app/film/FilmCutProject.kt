package com.androidharness.app.film

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A user-authorized, single-source non-destructive cut.
 * A content URI points to the user's document and is never uploaded by this module.
 * The JSON project is deliberately separate from a rendered MP4.
 */
@Serializable
data class FilmCutProject(
    val version: Int = 1,
    val title: String,
    val sourceUri: String,
    val startMs: Long,
    val endMs: Long,
) {
    fun validationError(durationMs: Long? = null): String? = when {
        version != 1 -> "Unsupported film project version."
        title.isBlank() || title.length > 120 || title.any { it.isISOControl() } -> "Enter a title of up to 120 characters."
        !sourceUri.startsWith("content://") || sourceUri.length > 2048 ||
            sourceUri.any { it.isISOControl() } -> "Choose a local video using the document picker."
        startMs < 0 || endMs <= startMs -> "The end must be after the start."
        endMs - startMs > MAX_CLIP_MS -> "The selected cut is too long."
        durationMs != null && (durationMs <= 0 || endMs > durationMs) -> "The cut exceeds the source video duration."
        else -> null
    }

    fun toPortableJson(): String {
        require(validationError() == null) { "The film project is invalid." }
        return Json.encodeToString(serializer(), this)
    }

    companion object {
        const val MAX_CLIP_MS = 6L * 60L * 60L * 1000L

        fun fromPortableJson(data: String): FilmCutProject {
            require(data.length <= 16_384) { "Film project is too large." }
            val project = Json { ignoreUnknownKeys = false }.decodeFromString(serializer(), data)
            require(project.validationError() == null) { "Film project is invalid." }
            return project
        }
    }
}
