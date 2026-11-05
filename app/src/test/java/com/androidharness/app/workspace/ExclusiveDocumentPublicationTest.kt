package com.androidharness.app.workspace

import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExclusiveDocumentPublicationTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun rejected(block: () -> Unit) = assertTrue(runCatching(block).isFailure)

    @Test fun existingFilesAndDanglingLinksAreNeverReplaced() {
        val root = tmp.newFolder()
        val captured = root.canonicalFile.toPath()
        root.resolve("saved.txt").writeText("previous data")
        rejected { ExclusiveDocumentPublication.publish(root, captured, "saved.txt", "new".toByteArray()) }
        assertEquals("previous data", root.resolve("saved.txt").readText())
        val dangling = root.toPath().resolve("link.txt")
        Files.createSymbolicLink(dangling, root.toPath().resolve("missing.txt"))
        rejected { ExclusiveDocumentPublication.publish(root, captured, "link.txt", "new".toByteArray()) }
        assertTrue(Files.isSymbolicLink(dangling))
        assertFalse(root.resolve("missing.txt").exists())
    }

    @Test fun concurrentCreatorsPublishExactlyOneCompletePayloadAndLeaveNoStagingFiles() {
        val root = tmp.newFolder()
        val gate = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val candidates = listOf(ByteArray(1_000_000) { 65 }, ByteArray(1_000_000) { 66 })
        try {
            val futures = candidates.map { bytes -> executor.submit(Callable {
                gate.await()
                runCatching { ExclusiveDocumentPublication.publish(root, root.canonicalFile.toPath(), "report.bin", bytes) }.isSuccess
            }) }
            gate.countDown()
            assertEquals(1, futures.count { it.get(10, TimeUnit.SECONDS) })
            val actual = root.resolve("report.bin").readBytes()
            assertTrue(candidates.any { it.contentEquals(actual) })
            assertEquals(listOf("report.bin"), root.listFiles()!!.filter { it.isFile }.map { it.name })
        } finally { executor.shutdownNow() }
    }

    @Test fun traversalAndSymlinkParentsCannotWriteIntoAnotherProject() {
        val root = tmp.newFolder()
        val other = tmp.newFolder()
        val captured = root.canonicalFile.toPath()
        for (path in listOf("../escape.txt", "/absolute.txt", "a/../escape.txt", "a//b", "a\\b")) {
            rejected { ExclusiveDocumentPublication.publish(root, captured, path, byteArrayOf(1)) }
        }
        Files.createSymbolicLink(root.toPath().resolve("other"), other.toPath())
        rejected { ExclusiveDocumentPublication.publish(root, captured, "other/private.txt", byteArrayOf(1)) }
        assertTrue(other.listFiles()!!.isEmpty())
    }

    @Test fun oversizedPayloadFailsBeforeCreatingOutputOrParents() {
        val root = tmp.newFolder()
        rejected { ExclusiveDocumentPublication.publish(root, root.canonicalFile.toPath(), "exports/large.bin", ByteArray(16 * 1024 * 1024 + 1)) }
        assertTrue(root.listFiles()!!.isEmpty())
    }
}
