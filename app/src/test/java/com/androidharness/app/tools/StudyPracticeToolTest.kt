package com.androidharness.app.tools

import com.androidharness.app.workspace.FileFs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class StudyPracticeToolTest {
    @Test fun readOnlyQuizWorksWithoutAccessingAnyProjectFiles() = runBlocking {
        val root = Files.createTempDirectory("saba-study").toFile()
        try {
            val tool = StudyPracticeTool()
            val context = ToolContext(FileFs(root), sessionId = "isolated-project-chat")
            val result = tool.execute(buildJsonObject {
                put("notes", "مالیات: مبلغ قانونی پرداختی به دولت")
                put("mode", "quiz")
            }, context)
            assertTrue(tool.isReadOnly)
            assertTrue(result.ok)
            assertTrue(result.output.contains("مالیات"))
            assertTrue(result.output.contains("پاسخ‌نامه:"))
            assertTrue(root.listFiles().orEmpty().isEmpty())
            assertTrue(runCatching { tool.execute(buildJsonObject { put("notes", "no structured answers"); put("mode", "quiz") }, context) }.isFailure)
            assertTrue(runCatching { tool.execute(buildJsonObject { put("notes", "Term: Definition"); put("mode", "execute") }, context) }.isFailure)
        } finally {
            root.deleteRecursively()
        }
    }
}
