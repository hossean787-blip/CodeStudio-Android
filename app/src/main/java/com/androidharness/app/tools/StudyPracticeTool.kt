package com.androidharness.app.tools

import com.androidharness.app.study.StudyPractice
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Offline, stateless educational practice; requires notes explicitly provided to the active chat. */
class StudyPracticeTool : Tool {
    override val name = "study_practice"
    override val description = "Generate offline flashcards, a self-test quiz or a review plan from explicit term: definition lines supplied by this chat. Does not OCR, transcribe, browse files or use a provider."
    override val isReadOnly = true
    override val parametersSchema = Schema.obj(
        mapOf(
            "notes" to Schema.string("User-supplied term: definition lines; maximum 20,000 chars. Do not use another project's notes."),
            "mode" to Schema.string("flashcards, quiz or plan"),
            "max_cards" to Schema.integer("Maximum number of pairs (1–30), default 20."),
        ),
        required = listOf("notes", "mode"),
    )

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val notes = args["notes"]?.jsonPrimitive?.contentOrNull ?: throw ToolFailure("Missing notes.")
        val mode = args["mode"]?.jsonPrimitive?.contentOrNull ?: throw ToolFailure("Missing mode.")
        val count = args["max_cards"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 20
        return try {
            ToolResult(true, StudyPractice.render(notes, mode, count))
        } catch (e: IllegalArgumentException) {
            throw ToolFailure(e.message ?: "Invalid study notes.")
        }
    }
}
