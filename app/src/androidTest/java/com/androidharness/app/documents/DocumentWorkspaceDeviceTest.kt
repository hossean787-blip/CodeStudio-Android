package com.androidharness.app.documents

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidharness.app.HarnessApp
import com.androidharness.app.agent.*
import com.androidharness.app.core.*
import com.androidharness.app.data.*
import com.androidharness.app.data.db.*
import com.androidharness.app.llm.*
import com.androidharness.app.tools.*
import com.androidharness.app.workspace.*
import java.io.File
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentWorkspaceDeviceTest {
    @Test fun approvedExportUsesWritableProjectAndUndoRemovesOnlyItsNewFile(): Unit = exercise(true, false, false)
    @Test fun deniedExportWritesNothingAndRecordsNoUndo(): Unit = exercise(false, false, false)
    @Test fun collisionPreservesExistingBinaryAndRecordsNoDeletionCheckpoint(): Unit = exercise(true, true, false)
    @Test fun readOnlySecondaryProjectCannotReceiveExports(): Unit = exercise(true, false, true)

    private fun exercise(approved: Boolean, collision: Boolean, readOnly: Boolean): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val c = (context.applicationContext as HarnessApp).container
        val name = "doc-fixture-${System.nanoTime()}.db"
        val root = File(context.cacheDir, name).apply { mkdirs() }
        val a = root.resolve("a").apply { mkdirs() }
        val b = root.resolve("b").apply { mkdirs() }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            db.dao().insertProject(ProjectEntity("a", "Alpha", "APP", null, 1))
            val sid = SessionRepository(db).createSession("Export", "a")
            val workspace = ChatWorkspaceFs("a", listOf(ChatWorkspaceBinding("a", "Alpha", true, FileFs(a)),
                ChatWorkspaceBinding("b", "Beta", false, FileFs(b))))
            val target = if (readOnly) b else a
            val original = byteArrayOf(0, -1, -2, 7)
            if (collision) target.resolve("report.txt").writeBytes(original)
            var round = 0
            val args = """{"workspace_id":"${if (readOnly) "b" else "a"}","format":"txt","path":"report.txt","text":"گزارش فارسی"}"""
            val provider = object : LlmProvider {
                override fun streamChat(config: ProviderConfig, apiKey: String, systemPrompt: String,
                    messages: List<ChatMessage>, tools: List<ToolSchema>, options: RequestOptions) = flow {
                    if (round++ == 0) emit(StreamEvent.ToolCallBatch(listOf(ToolCallData("export", "create_document", args))))
                    else emit(StreamEvent.TextDelta("Finished the requested attempt."))
                    emit(StreamEvent.Done(if (round == 1) "tool_calls" else "stop"))
                }
            }
            val checkpoints = CheckpointStore(db.dao())
            val engine = AgentEngine({ provider }, ToolRegistry(listOf(CreateDocumentTool())), checkpoints,
                c.images, c.linuxEnv, c.shizuku, c.skills)
            val events = engine.run(sid, "export-turn", ProviderConfig("fixture", "Fixture", ProviderType.OPENAI_COMPAT,
                "https://example.invalid", "fixture"), "fixture", listOf(ChatMessage(Role.USER, "Create a text export")),
                { PermissionMode.CONFIRM_ALL }, mutableSetOf(), workspace, RequestOptions(), 32000, AgentMode.ACT,
                projectConfiguration = com.androidharness.app.config.HarnessConfig()).onEach {
                    if (it is AgentEvent.ApprovalNeeded) it.request.response.complete(approved)
                }.toList()
            val succeeded = approved && !collision && !readOnly
            assertEquals(succeeded, events.filterIsInstance<AgentEvent.ToolFinished>().single().result.ok)
            val saved = db.dao().checkpointsForTurn(sid, "export-turn")
            if (succeeded) {
                assertEquals("گزارش فارسی", a.resolve("report.txt").readText())
                assertEquals(1, saved.size)
                assertFalse(saved.single().existedBefore)
                checkpoints.rewind(sid, "export-turn", workspace)
                assertFalse(a.resolve("report.txt").exists())
            } else {
                assertTrue(saved.isEmpty())
                if (collision) assertArrayEquals(original, target.resolve("report.txt").readBytes())
                else assertFalse(target.resolve("report.txt").exists())
            }
            assertTrue(b.listFiles()!!.isEmpty())
        } finally { db.close(); context.deleteDatabase(name); root.deleteRecursively() }
    }
}
