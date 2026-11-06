package com.androidharness.app.data

import com.androidharness.app.data.db.MemoryEntity
import org.junit.Assert.*
import org.junit.Test

class PrivateAgentProjectPolicyTest {
    private fun memory(scope: MemoryScope, projectId: String?): MemoryEntity =
        MemoryEntity(id = "mem", text = "private note", scope = scope.name, projectId = projectId,
            createdAt = 1L, updatedAt = 1L, section = "CHAT", agentId = "agent-a")

    @Test fun onlyPrivateMemoriesOfTheSameProjectAreVisible() {
        assertTrue(PrivateAgentProjectPolicy.visible(memory(MemoryScope.PROJECT, "p1"), "agent-a", "p1"))
        assertFalse(PrivateAgentProjectPolicy.visible(memory(MemoryScope.PROJECT, "p1"), "agent-a", "p2"))
        assertFalse(PrivateAgentProjectPolicy.visible(memory(MemoryScope.PROJECT, "p1"), "agent-a", null))
        assertFalse(PrivateAgentProjectPolicy.visible(memory(MemoryScope.GLOBAL, null), "agent-a", "p1"))
        assertTrue(PrivateAgentProjectPolicy.visible(memory(MemoryScope.GLOBAL, null), null, "p1"))
    }

    @Test fun privateAgentWritesRequireProjectAndNeverFallbackToGlobal() {
        assertEquals(MemoryScope.PROJECT,
            PrivateAgentProjectPolicy.writeScope("agent-a", "p1", MemoryScope.GLOBAL))
        assertEquals(MemoryScope.GLOBAL,
            PrivateAgentProjectPolicy.writeScope(null, null, MemoryScope.GLOBAL))
        assertTrue(runCatching {
            PrivateAgentProjectPolicy.writeScope("agent-a", null, MemoryScope.GLOBAL)
        }.isFailure)
    }
}
