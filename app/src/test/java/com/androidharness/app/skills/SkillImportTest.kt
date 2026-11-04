package com.androidharness.app.skills

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
