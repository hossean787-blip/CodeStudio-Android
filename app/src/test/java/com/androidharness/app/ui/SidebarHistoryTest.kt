package com.androidharness.app.ui

import com.androidharness.app.data.db.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarHistoryTest {
    @Test fun historyOrdersAllActiveChatsByMostRecentUpdateWithoutDuplicatingPinnedChats() {
        fun chat(id: String, updated: Long) =
            SessionEntity(id, id, createdAt = 1L, updatedAt = updated)
        val sessions = listOf(
            chat("old-project", 2L),
            chat("pinned", 10L),
            chat("archived", 100L),
            chat("same-z", 5L),
            chat("same-a", 5L),
        )
        assertEquals(
            listOf("pinned", "same-a", "same-z", "old-project"),
            SidebarChats.history(sessions, setOf("archived")).map { it.id },
        )
        assertEquals(sessions.size, SidebarChats.history(sessions, emptySet()).size)
    }

    @Test fun emptyHistoryShowsNoEntries() {
        assertEquals(emptyList<SessionEntity>(), SidebarChats.history(emptyList(), emptySet()))
    }
}
