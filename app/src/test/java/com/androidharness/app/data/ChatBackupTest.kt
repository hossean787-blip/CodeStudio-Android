package com.androidharness.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBackupTest {

    @Test fun `backup separates recovery audit and restores exact order and user text`() {
        val text = com.androidharness.app.agent.RunRecovery.CONTINUE_PROMPT
        val resume = message(com.androidharness.app.agent.RunRecovery.CHAT_CONTINUATION_ID_PREFIX + "fixture", text)
        val original = session("chat", resume, message("user", text), resume.copy(id = resume.id + "2"), message("answer"))
        val decoded = ChatBackupCodec.decode(ChatBackupCodec.encode(ChatBackupFile(sessions = listOf(original)))).sessions.single()
        assertEquals(listOf("user", "answer"), decoded.messages.map { it.id })
        assertEquals(listOf(0, 2), decoded.recoveryAudit.map { it.position })
        assertEquals(original.messages, decoded.allMessages())
        assertEquals(text, decoded.messages.first().text)
        assertEquals(decoded, decoded.withSeparatedRecoveryAudit())
    }

    @Test fun `legacy backup reserved ids become audit without guessing ordinary user text`() {
        val prefix = com.androidharness.app.agent.RunRecovery.CHAT_CONTINUATION_ID_PREFIX
        val text = """{"format":"androidharness-chats","version":5,"sessions":[{"id":"legacy","title":"Legacy","createdAt":1,"updatedAt":1,"messages":[{"id":"${prefix}old","role":"USER","text":"identical","createdAt":1},{"id":"user","role":"USER","text":"identical","createdAt":1}]}]}"""
        val restored = ChatBackupCodec.decode(text).sessions.single()
        assertEquals(listOf("user"), restored.messages.map { it.id })
        assertEquals(listOf(prefix + "old", "user"), restored.allMessages().map { it.id })
        // An older importer must reject the new format instead of ignoring its recoveryAudit section.
        val reexported = ChatBackupCodec.encode(ChatBackupFile(version = 5, sessions = listOf(restored)))
        assertTrue(reexported.contains("\"version\":6"))
        assertEquals(restored.allMessages(), ChatBackupCodec.decode(reexported).sessions.single().allMessages())
    }

    @Test fun `malformed audit cannot reclassify an ordinary user message`() {
        val text = """{"sessions":[{"id":"s","title":"S","createdAt":1,"updatedAt":1,"recoveryAudit":[{"position":0,"message":{"id":"user","role":"USER","text":"ordinary","createdAt":1}}]}]}"""
        try {
            ChatBackupCodec.decode(text)
            org.junit.Assert.fail("Expected invalid audit rejection")
        } catch (expected: ChatBackupException) {
            assertTrue(expected.message!!.contains("Invalid recovery audit"))
        }
    }

    private fun session(id: String, vararg messages: BackupMessage) = BackupSession(
        id = id,
        title = "Chat $id",
        createdAt = 1_000,
        updatedAt = 2_000,
        messages = messages.toList(),
    )

    private fun message(id: String, text: String = "hello") = BackupMessage(
        id = id,
        role = "USER",
        text = text,
        createdAt = 1_500,
    )

    @Test
    fun `plan skips sessions whose id already exists`() {
        val incoming = listOf(session("a"), session("b"), session("c"))
        val plan = ChatBackupMerge.plan(setOf("a", "x"), incoming)
        assertEquals(listOf("b", "c"), plan.toImport.map { it.id })
        assertEquals(1, plan.skipped)
    }

    @Test
    fun `plan with an empty database imports everything`() {
        val incoming = listOf(session("a"), session("b"))
        val plan = ChatBackupMerge.plan(emptySet(), incoming)
        assertEquals(2, plan.toImport.size)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun `encode then decode round-trips sessions and messages`() {
        val file = ChatBackupFile(
            exportedAt = 99,
            sessions = listOf(
                session(
                    "a",
                    message("m1"),
                    BackupMessage(
                        id = "m2",
                        role = "ASSISTANT",
                        text = "hi",
                        toolCallsJson = """[{"name":"sh"}]""",
                        toolName = "sh",
                        isError = true,
                        thinking = "hmm",
                        thinkingMs = 42,
                        outputTokens = 520,
                        generationMs = 10_000,
                        turnId = "t1",
                        createdAt = 2_500,
                    ),
                ),
            ),
        )
        assertEquals(file, ChatBackupCodec.decode(ChatBackupCodec.encode(file)))
    }

    @Test
    fun `encode then decode keeps memories and prompt snippets`() {
        val file = ChatBackupFile(
            exportedAt = 99,
            sessions = listOf(session("a", message("m1"))),
            memories = listOf(
                BackupMemory(
                    id = "memory-1",
                    text = "Prefers concise answers",
                    category = "PREFERENCE",
                    scope = "PROJECT",
                    projectId = "project-1",
                    pinned = true,
                    createdAt = 1_000,
                    updatedAt = 2_000,
                ),
            ),
            snippets = listOf(BackupSnippet("snippet-1", "review", "Review this diff")),
        )
        val encoded = ChatBackupCodec.encode(file)
        val decoded = ChatBackupCodec.decode(encoded)
        assertEquals(file.memories, decoded.memories)
        assertEquals(file.snippets, decoded.snippets)
        assertEquals(6, ChatBackupCodec.VERSION)
        assertTrue(encoded.contains("\"version\":${ChatBackupCodec.VERSION}"))
    }

    @Test
    fun `decode tolerates unknown keys and missing optional fields`() {
        val text = """
            {"format":"androidharness-chats","version":1,"exportedAt":5,
             "futureField":true,
             "sessions":[{"id":"a","title":"T","createdAt":1,"updatedAt":2,
                          "messages":[{"id":"m1","role":"USER","text":"x","createdAt":3}],
                          "futureSessionField":1}]}
        """.trimIndent()
        val file = ChatBackupCodec.decode(text)
        assertEquals(1, file.sessions.size)
        val m = file.sessions[0].messages[0]
        assertEquals("x", m.text)
        assertEquals("[]", m.toolCallsJson)
        assertEquals(0, m.outputTokens)
        assertEquals(0L, m.generationMs)
    }

    @Test
    fun `decode rejects foreign-format files`() {
        val text = """{"format":"other-app","version":1,"exportedAt":0,"sessions":[]}"""
        try {
            ChatBackupCodec.decode(text)
            throw AssertionError("expected ChatBackupException")
        } catch (e: ChatBackupException) {
            assertEquals("Not a chat backup file", e.message)
        }
    }

    @Test
    fun `decode rejects backups from a newer app version`() {
        val text = """{"format":"androidharness-chats","version":999,"exportedAt":0,"sessions":[]}"""
        try {
            ChatBackupCodec.decode(text)
            throw AssertionError("expected ChatBackupException")
        } catch (e: ChatBackupException) {
            assertTrue(e.message!!.contains("newer version"))
        }
    }

    @Test
    fun `decode rejects garbage`() {
        try {
            ChatBackupCodec.decode("definitely not json")
            throw AssertionError("expected ChatBackupException")
        } catch (e: ChatBackupException) {
            assertEquals("Not a chat backup file", e.message)
        }
    }
}
