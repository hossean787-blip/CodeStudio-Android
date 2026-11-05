package com.androidharness.app.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedSourceSubmoduleTest {
    private val first = "a".repeat(40)
    private val second = "b".repeat(40)

    @Test fun emptyStatusIsACompleteProjectWithNoSubmodules() {
        assertTrue(pinnedSubmodulesMatchGitlinks(""))
        assertTrue(pinnedSubmodulesMatchGitlinks("\n"))
    }

    @Test fun recursivelyInitializedGitlinksMustMatchExactCommits() {
        val status = " $first third_party/proot (heads/main)\n" +
            " $second third_party/libandroid-shmem (v1.0)\n"
        assertTrue(pinnedSubmodulesMatchGitlinks(status))
    }

    @Test fun missingMovedAndConflictedNestedRepositoriesAreRejected() {
        val clean = " $first third_party/proot (heads/main)"
        for (prefix in listOf('-', '+', 'U')) {
            assertFalse(pinnedSubmodulesMatchGitlinks(clean + "\n" +
                "$prefix$second third_party/other (heads/main)"))
        }
    }

    @Test fun malformedStatusAndErrorMessagesAreNeverTreatedAsSuccess() {
        assertFalse(pinnedSubmodulesMatchGitlinks("fatal: no submodule mapping found"))
        assertFalse(pinnedSubmodulesMatchGitlinks(" $first"))
        assertFalse(pinnedSubmodulesMatchGitlinks(" $first third_party/x\n--- truncation ---"))
        assertFalse(pinnedSubmodulesMatchGitlinks(" $first ../outside"))
    }
}
