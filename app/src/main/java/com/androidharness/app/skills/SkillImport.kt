package com.androidharness.app.skills

import java.io.Reader

/** Read only bounded SKILL.md instructions. Caller must offer review before saving. */
fun readSkillDocument(reader: Reader): String {
    val max = ParsedSkill.MAX_USER_CHARS
    val buffer = CharArray(2048)
    val result = StringBuilder()
    while (result.length <= max) {
        val count = reader.read(buffer, 0, minOf(buffer.size, max + 1 - result.length))
        if (count < 0) break
        if (count == 0) continue
        result.append(buffer, 0, count)
    }
    if (result.length > max) {
        throw SkillParseException("SKILL.md is too long (maximum $max characters).")
    }
    return SkillParser.validateUserContent(result.toString()).raw
}
