package com.androidharness.app.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GitLfsSourceDetectionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun ordinaryTextAttributesDoNotRequireGitLfs() {
        val root = temp.newFolder("ordinary")
        File(root, ".gitattributes").writeText("*.sh text eol=lf\n")
        assertFalse(sourceUsesGitLfs(root))
    }

    @Test fun syntacStyleRuntimeBundleRequiresGitLfs() {
        val root = temp.newFolder("syntac")
        File(root, ".gitattributes").writeText(
            "# filter=lfs is mentioned in a comment\n" +
            "assets/runtime/*.bundle filter=lfs diff=lfs merge=lfs -text\n"
        )
        assertTrue(sourceUsesGitLfs(root))
    }

    @Test fun detectsLfsRulesInNestedSubmoduleTree() {
        val root = temp.newFolder("parent")
        val module = File(root, "reference/omp").apply { mkdirs() }
        File(module, ".gitattributes").writeText("models/*.gguf filter=lfs diff=lfs merge=lfs -text\n")
        assertTrue(sourceUsesGitLfs(root))
    }

    @Test fun commentsAloneDoNotTriggerLfsDownload() {
        val root = temp.newFolder("comment")
        File(root, ".gitattributes").writeText("# *.bin filter=lfs\n*.txt text\n")
        assertFalse(sourceUsesGitLfs(root))
    }

    @Test fun gitMetadataIsNeverSearchedForWorkingTreeLfsFilters() {
        val root = temp.newFolder("metadata")
        val gitDir = File(root, ".git").apply { mkdirs() }
        File(gitDir, ".gitattributes").writeText("*.bin filter=lfs\n")
        assertFalse(sourceUsesGitLfs(root))
    }

    @Test fun lfsListingRequiresMaterializedObjectsNotPointers() {
        val oid = "a".repeat(64)
        assertTrue(lfsCheckoutIsHydrated(""))
        assertTrue(lfsCheckoutIsHydrated("$oid * models/ready.gguf\n"))
        assertFalse(lfsCheckoutIsHydrated("$oid - models/missing.gguf\n"))
        assertFalse(lfsCheckoutIsHydrated("$oid * models/ready.gguf\n$oid - models/missing.gguf\n"))
    }

    @Test fun lfsListingRejectsMalformedOrTruncatedOutput() {
        val oid = "b".repeat(64)
        assertFalse(lfsCheckoutIsHydrated("fatal: could not read LFS objects"))
        assertFalse(lfsCheckoutIsHydrated("$oid *"))
        assertFalse(lfsCheckoutIsHydrated("${oid.take(10)} * models/truncated.gguf"))
    }
}
