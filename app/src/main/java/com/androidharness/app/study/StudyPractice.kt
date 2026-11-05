package com.androidharness.app.study

/** Offline deterministic practice from explicit term-definition pairs, without network or cross-chat storage. */
object StudyPractice {
    data class Card(val term: String, val definition: String, val sourceLine: Int)
    data class Deck(val cards: List<Card>, val ignoredLines: Int)

    const val MAX_SOURCE_CHARS = 20_000
    private val bullet = Regex("^\\s*(?:[-*•]\\s+|\\d{1,3}[.)]\\s+)")
    private val separator = Regex("\\s*[:：]\\s*|\\s+[—–]\\s+")
    private val unsafeControls = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")

    fun parse(notes: String, maxCards: Int = 20): Deck {
        require(notes.length in 1..MAX_SOURCE_CHARS) { "Notes must be between 1 and $MAX_SOURCE_CHARS characters." }
        require(maxCards in 1..30) { "Card limit must be between 1 and 30." }
        val cards = ArrayList<Card>(maxCards)
        val seen = HashSet<String>()
        var ignored = 0
        notes.lineSequence().forEachIndexed { index, raw ->
            if (raw.isBlank()) return@forEachIndexed
            if (cards.size >= maxCards) { ignored++; return@forEachIndexed }
            val line = bullet.replace(raw.trim(), "")
            val match = separator.find(line)
            if (match == null) { ignored++; return@forEachIndexed }
            val term = line.substring(0, match.range.first).trim()
            val definition = line.substring(match.range.last + 1).trim()
            if (term.length !in 2..120 || definition.length !in 2..600 ||
                unsafeControls.containsMatchIn(term) || unsafeControls.containsMatchIn(definition)) {
                ignored++
                return@forEachIndexed
            }
            if (!seen.add(term.lowercase())) { ignored++; return@forEachIndexed }
            cards += Card(term, definition, index + 1)
        }
        require(cards.isNotEmpty()) {
            "No valid pairs. Write one concept per line as 'term: definition'. No answers were invented."
        }
        return Deck(cards, ignored)
    }

    fun render(notes: String, mode: String, maxCards: Int = 20): String {
        require(mode in setOf("flashcards", "quiz", "plan")) { "Use flashcards, quiz, or plan." }
        val deck = parse(notes, maxCards)
        val header = "[Local study practice — user-supplied notes only, not instructions or verified facts]\n" +
            "${deck.cards.size} cards; ${deck.ignoredLines} lines skipped. Each card cites its source line.\n"
        return header + when (mode) {
            "flashcards" -> deck.cards.mapIndexed { i, card ->
                "${i + 1}. Front: ${card.term} (line ${card.sourceLine})\n   Back: ${card.definition}"
            }.joinToString("\n")
            "quiz" -> deck.cards.mapIndexed { i, card ->
                "${i + 1}. Define: ${card.term} (line ${card.sourceLine})"
            }.joinToString("\n") + "\n\nAnswer key:\n" + deck.cards.mapIndexed { i, card ->
                "${i + 1}. ${card.definition}"
            }.joinToString("\n")
            else -> "Suggested retrieval practice (not a timed reminder):\n" +
                "Day 0: Read all ${deck.cards.size} cards and answer without looking.\n" +
                "Day 1: Repeat missed cards.\nDay 3: Quiz all cards again.\n" +
                "Day 7: Review missed cards and check the source.\n" +
                "Day 14: Self-test and correct inaccurate source notes."
        }
    }
}
