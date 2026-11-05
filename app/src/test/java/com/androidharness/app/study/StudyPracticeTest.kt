package com.androidharness.app.study

import org.junit.Assert.*
import org.junit.Test

class StudyPracticeTest {
    @Test fun persianAndEnglishNotesKeepLineCitationsAndSkipUnsupportedProse() {
        val deck = StudyPractice.parse("مفهوم: تعریف کوتاه\nThis is ordinary prose\n- Audit: Independent assessment\nمفهوم: پاسخ تکراری")
        assertEquals(2, deck.cards.size)
        assertEquals(listOf(1, 3), deck.cards.map { it.sourceLine })
        assertEquals(2, deck.ignoredLines)
        assertEquals("تعریف کوتاه", deck.cards[0].definition)
    }

    @Test fun quizContainsAnswersVerbatimWithoutInventingKnowledge() {
        val quiz = StudyPractice.render("نرخ تورم: رشد سطح عمومی قیمت‌ها", "quiz")
        assertTrue(quiz.contains("«نرخ تورم» را تعریف کنید. (خط 1)"))
        assertTrue(quiz.contains("پاسخ‌نامه:"))
        assertTrue(quiz.contains("رشد سطح عمومی قیمت‌ها"))
        assertFalse(quiz.contains("ChatGPT"))
    }

    @Test fun rejectsFreeTextUnsafeLengthsAndUnknownModes() {
        assertTrue(runCatching { StudyPractice.parse("Article without a structured answer") }.isFailure)
        assertTrue(runCatching { StudyPractice.parse("a: b") }.isFailure)
        assertTrue(runCatching { StudyPractice.parse("X: " + "a".repeat(20_001)) }.isFailure)
        assertTrue(runCatching { StudyPractice.render("Term: definition", "shell") }.isFailure)
        assertTrue(runCatching { StudyPractice.parse("Term: definition", 31) }.isFailure)
    }

    @Test fun outputBoundedAndPlanContainsNoCalendarClaim() {
        val notes = (1..40).joinToString("\n") { "Term$it: Explanation$it" }
        val flashcards = StudyPractice.render(notes, "flashcards", 3)
        assertTrue(flashcards.contains("3 cards; 37 lines skipped"))
        assertFalse(flashcards.contains("Term4"))
        assertTrue(StudyPractice.render(notes, "plan", 2).contains("Day 14"))
        assertFalse(StudyPractice.render(notes, "plan", 2).contains("2026"))
    }
    @Test fun explicitPersianOutputSupportsEnglishOnlySourceMaterial() {
        val result = StudyPractice.render("Audit: Independent assessment", "flashcards", language = "fa")
        assertTrue(result.contains("پرسش: Audit"))
        assertTrue(result.contains("پاسخ: Independent assessment"))
    }

}
