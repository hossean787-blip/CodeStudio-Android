package com.androidharness.app.skills

import java.io.Reader
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout

/** Open, bounded read, validation and close all run off the UI thread.
 * Cancellation interrupts blocking IO; providers must cooperate with interruption.
 * use preserves the primary failure if close also fails. No URI/content is logged.
 */
suspend fun loadSkillDocument(openReader: () -> Reader): String = withTimeout(30_000L) {
    runInterruptible(Dispatchers.IO) {
        openReader().use(::readSkillDocument)
    }
}


/** Read only bounded SKILL.md instructions. Caller must offer review before saving. */
fun readSkillDocument(reader: Reader): String {
    val max = ParsedSkill.MAX_USER_CHARS
    val buffer = CharArray(2048)
    val result = StringBuilder()
    var emptyReads = 0
    while (result.length <= max) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Skill import cancelled")
        val count = reader.read(buffer, 0, minOf(buffer.size, max + 1 - result.length))
        if (count < 0) break
        if (count == 0) {
            if (++emptyReads >= 16) throw IOException("Selected file reader made no progress")
            continue
        }
        emptyReads = 0
        result.append(buffer, 0, count)
    }
    if (result.length > max) {
        throw SkillParseException("SKILL.md is too long (maximum $max characters).")
    }
    return SkillParser.validateUserContent(result.toString()).raw
}
