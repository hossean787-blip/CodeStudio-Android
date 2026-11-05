package com.androidharness.app.workspace

import com.androidharness.app.documents.DocumentExportSink
import com.androidharness.app.tools.CreateDocumentTool
import com.androidharness.app.tools.ToolContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Security gate: do not advertise automatic export until publication is proven grant-safe. */
class DocumentPublicationTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun unverifiedWorkspacePublicationIsNotAdvertisedOrActivated() {
        val root = tmp.newFolder()
        root.resolve("saved.txt").writeText("previous data")
        val workspace: WorkspaceFs = FileFs(root)
        assertFalse(workspace is DocumentExportSink)
        assertFalse(CreateDocumentTool().isAvailable(ToolContext(workspace, sessionId = "fixture")))
        assertEquals("previous data", root.resolve("saved.txt").readText())
        assertEquals(listOf("saved.txt"), root.listFiles()!!.map { it.name })
    }
}
