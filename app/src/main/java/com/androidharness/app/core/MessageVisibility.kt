package com.androidharness.app.core

/** Persisted origin marker, reserved for app-generated recovery controls. Never infer origin from text.
 * Keep the historical prefix: Room, answer versions, and backups preserve these IDs verbatim.
 */
object MessageVisibility {
    const val INTERNAL_RESUME_ID_PREFIX = "internal-chat-continuation-"
    fun isInternalResume(id: String?): Boolean = id?.startsWith(INTERNAL_RESUME_ID_PREFIX) == true
    fun visible(messages: List<ChatMessage>): List<ChatMessage> = messages.filterNot { isInternalResume(it.id) }
}
