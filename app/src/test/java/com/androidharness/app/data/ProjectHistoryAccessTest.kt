package com.androidharness.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectHistoryAccessTest {
    @Test fun historyIsScopedToTheSameExplicitProject() {
        assertTrue(ProjectHistoryAccess.sameProject("project-1", "project-1"))
        assertFalse(ProjectHistoryAccess.sameProject("project-1", "project-2"))
        assertFalse(ProjectHistoryAccess.sameProject("project-1", null))
        assertFalse(ProjectHistoryAccess.sameProject(null, "project-1"))
        assertFalse(ProjectHistoryAccess.sameProject(null, null))
        assertFalse(ProjectHistoryAccess.sameProject("", ""))
    }
}
