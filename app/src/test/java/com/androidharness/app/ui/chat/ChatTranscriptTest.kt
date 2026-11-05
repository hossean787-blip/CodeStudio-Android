package com.androidharness.app.ui.chat

import com.androidharness.app.core.ChatMessage
import com.androidharness.app.core.MessageVisibility
import com.androidharness.app.core.Role
import org.junit.Assert.*
import org.junit.Test

class ChatTranscriptTest {
    private fun message(role: Role, text: String, id: String? = null, toolCallId: String? = null) =
        ChatMessage(role = role, text = text, id = id, toolCallId = toolCallId)

    @Test fun exportsOnlyAuthoredUserAndVisibleAssistantMarkdown() {
        val internal = MessageVisibility.INTERNAL_RESUME_ID_PREFIX + "1"
        val items = listOf(
            message(Role.SYSTEM, "SECRET INTERNAL INSTRUCTION"),
            message(Role.USER, "visible question"),
            message(Role.USER, "INTERNAL RESUME", internal),
            message(Role.TOOL, "TOOL SECRET"),
            message(Role.ASSISTANT, "subagent private", toolCallId = "parent-tool"),
            message(Role.ASSISTANT, "| نام | مقدار |\n|---|---|\n| الف | ۱ |\n\n"+ "```kotlin\nval x = 1\n```"),
        )
        val result = exportChatTranscript("مکالمه", items)
        assertFalse(result.olderContentOmitted)
        assertTrue(result.markdown.contains("visible question"))
        assertTrue(result.markdown.contains("| نام | مقدار |"))
        assertTrue(result.markdown.contains("```kotlin\nval x = 1\n```"))
        assertFalse(result.markdown.contains("SECRET"))
        assertFalse(result.markdown.contains("INTERNAL RESUME"))
        assertFalse(result.markdown.contains("subagent private"))
    }

    @Test fun onlyKeepsNewestWholeBlocksWithinAndroidShareBudget() {
        val first = message(Role.USER, "old ".repeat(600))
        val last = message(Role.ASSISTANT, "Newest reply\n\n```python\nprint(1)\n```")
        val result = exportChatTranscript("Session\nAnother line", listOf(first, last), maxCharacters = 1024)
        assertTrue(result.olderContentOmitted)
        assertFalse(result.markdown.contains("old old"))
        assertTrue(result.markdown.contains("Newest reply"))
        assertTrue(result.markdown.contains("Session Another line"))
        assertTrue(result.markdown.length <= 1024)
    }

    @Test fun missingMessagesProduceHeaderOnlyWithoutOtherSessionData() {
        val result = exportChatTranscript(" ", listOf(message(Role.SYSTEM, "context only")))
        assertEquals("# گفتگو\n\n", result.markdown)
    }
}
