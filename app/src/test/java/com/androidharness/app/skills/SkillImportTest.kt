package com.androidharness.app.skills

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.launch

class SkillImportTest {
    private val minimal = "---\nname: safe-example\ndescription: Example open-format skill\n---\n# Example\nDo a safe task.\n"

    @Test fun validExternalSkillCanBeReviewed() {
        val imported = readSkillDocument(StringReader(minimal))
        assertEquals("safe-example", SkillParser.parse(imported).name)
        assertTrue(imported.contains("Do a safe task."))
    }

    @Test(expected = SkillParseException::class)
    fun oversizedFileIsRejected() {
        readSkillDocument(StringReader(minimal + "a".repeat(ParsedSkill.MAX_USER_CHARS)))
    }

    @Test(expected = SkillParseException::class)
    fun invalidFrontmatterIsRejected() {
        readSkillDocument(StringReader("name: not-a-skill"))
    }

    @Test fun brokenZeroReaderIsBounded() {
        var calls = 0
        val reader = object : java.io.Reader() {
            override fun read(b: CharArray, off: Int, len: Int): Int { calls++; return 0 }
            override fun close() {}
        }
        try { readSkillDocument(reader); error("Expected read failure") }
        catch (_: java.io.IOException) { assertEquals(16, calls) }
    }

    @Test fun loaderOpensOffCallerThreadAndCloses() = kotlinx.coroutines.runBlocking {
        val caller = Thread.currentThread()
        var closed = false
        val result = loadSkillDocument {
            assertTrue(Thread.currentThread() !== caller)
            object : StringReader(minimal) {
                override fun close() { closed = true; super.close() }
            }
        }
        assertEquals(minimal, result)
        assertTrue(closed)
    }

    @Test fun readFailureSurvivesCloseFailure() = kotlinx.coroutines.runBlocking {
        val failure = java.io.IOException("read failed")
        val closeFailure = java.io.IOException("close failed")
        try {
            loadSkillDocument {
                object : java.io.Reader() {
                    override fun read(b: CharArray, off: Int, len: Int): Int = throw failure
                    override fun close(): Unit = throw closeFailure
                }
            }
            error("Expected read failure")
        } catch (e: java.io.IOException) {
            // With JVM assertions enabled, coroutine stacktrace recovery copies this
            // IOException and retains the exact original as its cause. Verify that
            // original object, not only a matching message on an unrelated error.
            val primary = if (e === failure) e else {
                assertEquals(failure.javaClass, e.javaClass)
                assertTrue("Recovered exception must retain the original read failure", e.cause === failure)
                requireNotNull(e.cause)
            }
            assertTrue(primary === failure)
            assertTrue("Close failure must be suppressed on the original read failure",
                primary.suppressed.any { it === closeFailure })
        }
    }

    @Test fun cancellationInterruptsReadAndCloses() = kotlinx.coroutines.runBlocking {
        val started = java.util.concurrent.CountDownLatch(1)
        val closed = java.util.concurrent.CountDownLatch(1)
        val job = launchImport {
            loadSkillDocument {
                object : java.io.Reader() {
                    override fun read(b: CharArray, off: Int, len: Int): Int {
                        started.countDown()
                        Thread.sleep(60_000)
                        return -1
                    }
                    override fun close() { closed.countDown() }
                }
            }
        }
        assertTrue(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            started.await(5, java.util.concurrent.TimeUnit.SECONDS)
        })
        job.cancel()
        kotlinx.coroutines.withTimeout(5_000) { job.join() }
        assertTrue(closed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(job.isCancelled)
    }

    private fun kotlinx.coroutines.CoroutineScope.launchImport(block: suspend () -> Unit) =
        launch { block() }
}
