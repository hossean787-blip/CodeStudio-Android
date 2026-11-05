package com.androidharness.app.ui.chat

import com.androidharness.app.core.ChatMessage
import com.androidharness.app.core.MessageVisibility
import com.androidharness.app.core.Role
import com.androidharness.app.core.response.UserMessageContent

internal data class ChatTranscript(val markdown: String, val olderContentOmitted: Boolean)

/**
 * Formats one session only, preserving Markdown/code/table blocks in visible answers.
 * Database paging does not limit the caller's complete message collection.
 * System instructions, tools, internal recovery prompts, thinking and subagent turns are excluded.
 */
internal fun exportChatTranscript(
    title: String,
    history: List<ChatMessage>,
    maxCharacters: Int = 100_000,
): ChatTranscript {
    require(maxCharacters >= 1024)
    val safeTitle = title.replace('\n', ' ').replace('\r', ' ').trim().take(120).ifBlank { "گفتگو" }
    val heading = "# " + safeTitle + "\n\n"
    val note = "_بخش‌هایی از تاریخچه قدیمی به دلیل محدودیت حجم اشتراک‌گذاری حذف شده‌اند._\n\n"
    val available = maxCharacters - heading.length - note.length
    var used = 0
    var clipped = false
    val selected = ArrayList<String>()
    for (message in history.asReversed()) {
        val visible = when {
            MessageVisibility.isUserAuthored(message) ->
                UserMessageContent.visibleText(message.text)
            message.role == Role.ASSISTANT && message.toolCallId == null ->
                message.text
            else -> continue
        }.trim()
        if (visible.isBlank()) continue
        val label = if (message.role == Role.USER) "## کاربر\n\n" else "## دستیار\n\n"
        val block = label + visible + "\n\n"
        if (used + block.length > available) {
            clipped = true
            break
        }
        selected += block
        used += block.length
    }
    val result = heading + (if (clipped) note else "") + selected.asReversed().joinToString("")
    return ChatTranscript(result.take(maxCharacters), clipped)
}
