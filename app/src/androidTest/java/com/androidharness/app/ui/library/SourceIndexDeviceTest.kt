package com.androidharness.app.ui.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SourceIndexDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun sourceIndicesAreProjectIsolatedAndDoNotReadLegacyFile() {
        val idA = "test-a-" + UUID.randomUUID()
        val idB = "test-b-" + UUID.randomUUID()
        val a = File(context.filesDir, LibraryScope.fileName(idA))
        val b = File(context.filesDir, LibraryScope.fileName(idB))
        try {
            a.writeText("""[{"id":"a","name":"A","uri":"content://a","text":"private A"}]""")
            b.writeText("""[{"id":"b","name":"B","uri":"content://b","text":"private B"}]""")
            assertEquals("private A", SourceIndex.read(context, idA).single().text)
            assertEquals("private B", SourceIndex.read(context, idB).single().text)
            assertTrue(SourceIndex.read(context, "test-c-" + UUID.randomUUID()).isEmpty())
        } finally {
            a.delete()
            b.delete()
        }
    }

    @Test fun malformedIndexFailsClosedAndIsNotOverwritten() {
        val id = "test-corrupt-" + UUID.randomUUID()
        val file = File(context.filesDir, LibraryScope.fileName(id))
        try {
            file.writeText("incomplete-json")
            try {
                SourceIndex.read(context, id)
                fail("Corrupt data must not be treated as an empty source list")
            } catch (_: org.json.JSONException) { }
            assertEquals("incomplete-json", file.readText())
        } finally {
            file.delete()
        }
    }
}
