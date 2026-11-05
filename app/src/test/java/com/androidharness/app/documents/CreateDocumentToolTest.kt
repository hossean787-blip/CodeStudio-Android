package com.androidharness.app.documents

import com.androidharness.app.tools.CreateDocumentTool
import com.androidharness.app.tools.ToolContext
import com.androidharness.app.tools.ToolFailure
import com.androidharness.app.workspace.FileFs
import com.androidharness.app.workspace.WorkspaceFs
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CreateDocumentToolTest {
    /** Contract fake: tool must never publish before validation, nor use FsNode writes. */
    private class Sink(private val delegate: WorkspaceFs) : WorkspaceFs by delegate, DocumentExportSink {
        val writes = linkedMapOf<String, ByteArray>()
        override fun publishDocumentExclusive(relativePath: String, bytes: ByteArray): String {
            if (relativePath in writes) throw ToolFailure("Already exists")
            writes[relativePath] = bytes.copyOf()
            return relativePath
        }
    }
    private fun args(path: String = "report.txt", text: String = "سلام") = buildJsonObject {
        put("path", path); put("format", "txt"); put("text", text)
    }
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected ToolFailure") } catch (_: ToolFailure) { }
    }
    @Test fun exportUsesOnlySelectedSinkReturnsMetadataAndNeverOverwrites() = runBlocking {
        val firstRoot = Files.createTempDirectory("export-first").toFile()
        val secondRoot = Files.createTempDirectory("export-second").toFile()
        try {
            val a = Sink(FileFs(firstRoot)); val b = Sink(FileFs(secondRoot)); val tool = CreateDocumentTool()
            assertFalse(tool.isReadOnly)
            val result = tool.execute(args(), ToolContext(a))
            val output = Json.parseToJsonElement(result.output).jsonObject
            assertEquals("report.txt", output["path"]!!.jsonPrimitive.content)
            assertEquals("text/plain", output["mime"]!!.jsonPrimitive.content)
            assertEquals("سلام".toByteArray().size, output["bytes"]!!.jsonPrimitive.int)
            assertTrue(b.writes.isEmpty())
            rejected { tool.execute(args(text = "replacement"), ToolContext(a)) }
            assertEquals("سلام", a.writes.getValue("report.txt").toString(Charsets.UTF_8))
            assertEquals(0, firstRoot.listFiles()!!.size)
        } finally { firstRoot.deleteRecursively(); secondRoot.deleteRecursively() }
    }
    @Test fun unsupportedProviderInvalidInputsAndCancellationCannotPublish() = runBlocking {
        val root = Files.createTempDirectory("export-invalid").toFile()
        try {
            val plain = FileFs(root); val sink = Sink(plain); val tool = CreateDocumentTool()
            assertFalse(tool.isAvailable(ToolContext(plain)))
            rejected { tool.execute(args(), ToolContext(plain)) }
            listOf("../escape.txt", "/global.txt", "bad.pdf", "a//b.txt").forEach { path -> rejected { tool.execute(args(path), ToolContext(sink)) } }
            rejected { tool.execute(args(text = "x".repeat(100001)), ToolContext(sink)) }
            rejected { tool.execute(buildJsonObject { put("path", "x.txt"); put("format", "txt"); put("text", 12) }, ToolContext(sink)) }
            val job = launch(start = CoroutineStart.LAZY) { tool.execute(args(), ToolContext(sink)) }
            job.cancel(); job.join()
            assertTrue(sink.writes.isEmpty()); assertEquals(0, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
}
