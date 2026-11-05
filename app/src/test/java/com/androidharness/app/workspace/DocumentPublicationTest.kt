package com.androidharness.app.workspace

import com.androidharness.app.documents.DocumentExportSink
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentPublicationTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun localWorkspacePublishesANewCompleteDocumentWithoutReplacingIt() {
        val root = tmp.newFolder()
        val workspace: WorkspaceFs = FileFs(root)
        val sink = workspace as? DocumentExportSink
        assertNotNull("Local workspace lacks exclusive document publication", sink)
        assertEquals("exports/report.txt", sink!!.publishDocumentExclusive("exports/report.txt", "متن فارسی".toByteArray()))
        assertEquals("متن فارسی", root.resolve("exports/report.txt").readText())
        assertTrue(runCatching { sink.publishDocumentExclusive("exports/report.txt", "overwrite".toByteArray()) }.isFailure)
        assertEquals("متن فارسی", root.resolve("exports/report.txt").readText())
    }
}
