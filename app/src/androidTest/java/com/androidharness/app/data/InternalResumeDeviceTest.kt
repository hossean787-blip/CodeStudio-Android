package com.androidharness.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidharness.app.HarnessApp
import com.androidharness.app.agent.*
import com.androidharness.app.core.*
import com.androidharness.app.data.db.AppDatabase
import com.androidharness.app.llm.*
import com.androidharness.app.workspace.FileFs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disk-backed repository + actual engine boundary; provider is local and never uses the network. */
@RunWith(AndroidJUnit4::class)
class InternalResumeDeviceTest {
    @Test fun bothModesKeepRecoveryInContextAcrossReloadAndOutOfVisibleHistory(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val c = (context.applicationContext as HarnessApp).container
        val name = "resume-fixture-${System.nanoTime()}.db"
        val root = File(context.cacheDir, name).apply { mkdirs() }
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        val ids = mutableListOf<String>()
        val prompts = mutableMapOf<String, String>()
        val controls = mutableMapOf<String, String>()
        try {
            var repository = SessionRepository(db)
            for (kind in ConversationKind.entries) {
                val projectId = "project-${kind.name}"
                db.dao().insertProject(com.androidharness.app.data.db.ProjectEntity(projectId, kind.name, "APP", null, 1))
                val id = repository.createSession("New chat", projectId, ConversationConfig(kind))
                ids += id
                // Internal control cannot become the first-message title or occupy the user's prompt slot.
                controls[id] = repository.addMessage(id, RunRecovery.continuationMessage(), "turn-$id")
                prompts[id] = repository.addMessage(id, ChatMessage(Role.USER, RunRecovery.CONTINUE_PROMPT), "turn-$id")
                repository.addMessage(id, RunRecovery.continuationMessage(), "turn-$id")
                assertEquals(prompts[id], db.dao().firstUserMessage(id)?.id)
                repository.addMessage(id, ChatMessage(Role.ASSISTANT, "Saved answer"), "turn-$id")
                if (kind == ConversationKind.CHAT) {
                    repository.archiveChatAnswer(id, requireNotNull(prompts[id]))
                    assertFalse(repository.answerVersionPreview(id, requireNotNull(prompts[id])).any { MessageVisibility.isInternalResume(it.id) })
                }
            }
            db.close()
            db = open()
            repository = SessionRepository(db)
            var observed = emptyList<ChatMessage>()
            val provider = object : LlmProvider {
                override fun streamChat(config: ProviderConfig, apiKey: String, systemPrompt: String,
                    messages: List<ChatMessage>, tools: List<ToolSchema>, options: RequestOptions) = flow {
                    observed = messages.toList()
                    emit(StreamEvent.TextDelta("Resumed")); emit(StreamEvent.Done("stop"))
                }
            }
            val engine = AgentEngine({ provider }, c.registry, c.checkpoints, c.images, c.linuxEnv, c.shizuku, c.skills)
            for ((index, id) in ids.withIndex()) {
                assertEquals("project-${ConversationKind.entries[index].name}", repository.session(id)?.projectId)
                val history = repository.historyFor(id).second
                assertTrue(history.any { it.id == controls[id] && it.text == RunRecovery.CONTINUE_PROMPT })
                assertFalse(history.any { it.id == controls[ids[1 - index]] })
                val visible = MessageVisibility.visible(repository.messageWindowFlow(id, repository.messageWindowStart(id)).first().messages)
                assertEquals(listOf(prompts[id]), visible.filter { it.role == Role.USER }.map { it.id })
                assertEquals(listOf(prompts[id]), repository.searchMessagesInSession(id, "authoritative").map { it.messageId })
                assertEquals(listOf(prompts[id]), repository.searchMessagesInSession(id, "uthoritativ").map { it.messageId })
                assertEquals(listOf(prompts[id]), repository.searchMessages("authoritative", true).filter { it.sessionId == id }.map { it.messageId })
                engine.run(id, "turn-$id", ProviderConfig("fixture", "Fixture", ProviderType.OPENAI_COMPAT, "https://example.com", "fixture"),
                    "fixture", history, { PermissionMode.FULL_AUTO }, mutableSetOf(), FileFs(root), RequestOptions(),
                    32000, AgentMode.ACT, conversation = ConversationConfig(ConversationKind.entries[index]), repoMapEnabled = false,
                    projectConfiguration = com.androidharness.app.config.HarnessConfig()).toList()
                assertTrue(observed.any { it.text == RunRecovery.CONTINUE_PROMPT && it.id == controls[id] })
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
            root.deleteRecursively()
        }
    }
}
