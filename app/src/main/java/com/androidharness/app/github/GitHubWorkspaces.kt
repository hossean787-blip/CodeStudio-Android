package com.androidharness.app.github

import com.androidharness.app.AppContainer
import com.androidharness.app.workspace.FileFs
import com.androidharness.app.workspace.WorkspaceFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * Git prints a leading space for each initialized submodule at its pinned
 * gitlink commit. '-' means not initialized, '+' means moved, and 'U' means a
 * merge conflict. Never register partial or divergent nested source trees.
 */
internal fun pinnedSubmodulesMatchGitlinks(output: String): Boolean {
    val clean = Regex("^ [0-9a-f]{40} ([^\\r\\n]+)$")
    return output.lineSequence().filter { it.isNotBlank() }.all { line ->
        val body = clean.matchEntire(line)?.groupValues?.get(1) ?: return@all false
        val path = body.substringBefore(" (")
        path.isNotBlank() && !path.startsWith("/") &&
            !path.split('/').contains("..") && '\t' !in path
    }
}

/** Git LFS uses '*' for materialized files and '-' for pointer-only worktrees. */
internal fun lfsCheckoutIsHydrated(output: String): Boolean {
    val materialized = Regex("^[0-9a-fA-F]{64} \\* .+$")
    return output.lineSequence().filter { it.isNotBlank() }.all { materialized.matches(it) }
}

/** Detect tracked Git LFS filter declarations, including nested source trees. */
internal fun sourceUsesGitLfs(sourceRoot: File): Boolean {
    val marker = Regex("""(?:^|\s)filter=lfs(?:\s|$)""")
    return sourceRoot.walkTopDown()
        .onEnter { it.name != ".git" && !Files.isSymbolicLink(it.toPath()) }
        .filter { it.name == ".gitattributes" && it.isFile && !Files.isSymbolicLink(it.toPath()) }
        .any { attributes ->
            attributes.useLines { lines ->
                lines.any { line ->
                    !line.trimStart().startsWith("#") && marker.containsMatchIn(line)
                }
            }
        }
}

/** Phone-owned clones use private app storage and need no broad storage grant. */
class GitHubWorkspaces(private val c: AppContainer) : PullRequestPublisher {
    private val imports = Mutex()
    private val root = File(c.appContext.filesDir, "github-projects")
    private val pulls by lazy { GitHubPullRequests(c.github.api) }

    private suspend fun token(): String = c.github.accessToken()
        ?: error("Connect GitHub in Settings to open pull requests.")

    // Pull requests read the workspace and write only to GitHub, so a running task may open one.
    override suspend fun preparePullRequest(fs: WorkspaceFs, base: String?): PullRequestPlan = withContext(Dispatchers.IO) {
        val token = token()
        val repository = repository(fs)
        val name = repository.originRepository()
        val defaultBranch = pulls.defaultBranch(name, token)
        val branch = repository.pullRequestBranch(name, base?.trim()?.ifBlank { null } ?: defaultBranch)
        PullRequestPlan(branch, defaultBranch, pulls.findOpen(name, branch.branch, token))
    }

    override suspend fun openPullRequest(plan: PullRequestPlan, title: String, body: String, draft: Boolean): PullRequestInfo {
        plan.existing?.let { return it }
        val branch = plan.branch
        require(title.isNotBlank()) { "Enter a pull request title." }
        check(branch.ahead > 0) { "${branch.branch} has no commits that aren't already on ${branch.base}." }
        check(branch.pushed) {
            if (branch.remoteHead == null) "${branch.branch} isn't on GitHub yet. Push it first."
            else "GitHub's copy of ${branch.branch} doesn't match your latest commit. Push first."
        }
        return pulls.create(branch.repository, branch.branch, branch.base, title, body, draft, token())
    }

    override suspend fun baseBranches(repository: String): List<String> = pulls.branches(repository, token())

    private fun requireGitTools() = check(c.linuxEnv.gitToolsReady) {
        "Install or repair Git tools from Import from GitHub or Commit & push, then retry."
    }

    private fun repository(fs: WorkspaceFs): GitHubRepository {
        check(fs.shellRoot != null) {
            "These controls need a device workspace with shell access. Import the repository, or configure Git and credentials on your SSH host."
        }
        requireGitTools()
        return GitHubRepository { args ->
            val result = c.shellRouter.runWorkspace(gitCommand(args), fs, timeoutMs = 180_000, maxOutput = 256_000)
            // Never commit using an incomplete file list.
            if (result.rawOutput.length >= 256_000 || result.rawStderr.length >= 256_000)
                error("Git output is too large. Reduce the changes and refresh before publishing.")
            GitCommandResult(result.exitCode, result.rawOutput, result.rawStderr +
                if (result.timedOut) "\nCommand timed out. Check repository status before retrying." else "")
        }
    }

    suspend fun inspect(fs: WorkspaceFs): GitHubRepoState = repository(fs).inspect()

    suspend fun connect(fs: WorkspaceFs, url: String) = c.runManager.withIdleWorkspace(fs) {
        repository(fs).connect(url)
    }

    private suspend fun checkAccess(url: String, token: String) {
        val name = gitHubRepositoryUrl(url).removePrefix("https://github.com/").removeSuffix(".git")
        val repo = c.github.api.get("/repos/$name", token).jsonObject
        check(repo["archived"]?.jsonPrimitive?.booleanOrNull != true) { "This GitHub repository is archived. Unarchive it before publishing." }
        check((repo["permissions"] as? JsonObject)?.get("push")?.jsonPrimitive?.booleanOrNull != false) {
            "This GitHub account cannot push to $name. Ask for write access or connect a repository you can write to."
        }
    }

    suspend fun publish(fs: WorkspaceFs, state: GitHubRepoState, paths: Set<String>, message: String, commit: Boolean): String =
        c.runManager.withIdleWorkspace(fs) {
            c.github.withConnection { token ->
                check(token != null) { "Connect GitHub in Settings before publishing." }
                checkAccess(state.remoteUrl, token)
                c.refreshGitHubAuth()
                repository(fs).publish(state, paths, message, commit)
            }
        }

    suspend fun pushPreset(fs: WorkspaceFs, preset: GitHubPushPreset): String = c.runManager.withIdleWorkspace(fs) {
        c.github.withConnection { token ->
            check(token != null) { "Connect GitHub in Settings before publishing." }
            checkAccess(preset.remoteUrl, token)
            c.refreshGitHubAuth()
            repository(fs).runPreset(preset)
        }
    }

    suspend fun listRepositories(page: Int = 1): List<String> {
        val token = c.github.accessToken() ?: error("Sign in to list your repositories. A public repository can also be imported by URL.")
        return c.github.api.get("/user/repos?sort=updated&per_page=30&page=$page", token).jsonArray
            .mapNotNull { (it as? JsonObject)?.string("full_name") }
    }

    // Preserve the existing GitHub import API for all call sites (including trailing lambdas).
    suspend fun importRepository(input: String, activate: Boolean = true, onRegistered: (String) -> Unit = {}): String =
        importSource(input, activate, onRegistered, null)

    /** Import an immutable source revision without affecting other workspaces. */
    suspend fun importPinnedRepository(input: String, pinnedCommit: String, activate: Boolean = true,
        onRegistered: (String) -> Unit = {}): String =
        importSource(input, activate, onRegistered, gitHubCommitSha(pinnedCommit))

    private suspend fun importSource(input: String, activate: Boolean, onRegistered: (String) -> Unit,
        pinnedCommit: String?): String = imports.withLock { withContext(Dispatchers.IO) {
        val url = gitHubRepositoryUrl(input)
        val expectedCommit = pinnedCommit?.let(::gitHubCommitSha)
        requireGitTools()
        check(root.isDirectory || root.mkdirs()) { "Could not create the repository storage folder." }
        val name = url.substringAfterLast('/').removeSuffix(".git")
        val folder = File(root, "$name-${UUID.randomUUID().toString().take(8)}")
        // Clone to a new folder only. Failed imports are isolated and never replace an existing project.
        var registered = false
        try {
            val result = c.github.withConnection {
                c.refreshGitHubAuth()
                c.shellRouter.runWorkspace(gitCommand(listOf("clone", "--", url, folder.absolutePath)),
                    FileFs(root), timeoutMs = 600_000, maxOutput = 32_000)
            }
            if (result.exitCode != 0 || result.timedOut) {
                throw GitHubOperationFailure("Import", gitHubFailureHint(result.exitCode, result.rawStderr + result.rawOutput))
            }
            // A source-library import must point to exactly the reviewed upstream commit.
            // Checkout and verify before creating a workspace; failed imports are removed.
            if (expectedCommit != null) {
                val folderFs = FileFs(root)
                val checkout = c.shellRouter.runWorkspace(
                    gitCommand(listOf("-C", folder.absolutePath, "checkout", "--detach", expectedCommit)),
                    folderFs, timeoutMs = 180_000, maxOutput = 32_000)
                if (checkout.exitCode != 0 || checkout.timedOut)
                    throw GitHubOperationFailure("Pinned checkout", gitHubFailureHint(checkout.exitCode, checkout.rawStderr + checkout.rawOutput))
                val revision = c.shellRouter.runWorkspace(
                    gitCommand(listOf("-C", folder.absolutePath, "rev-parse", "--verify", "HEAD")),
                    folderFs, timeoutMs = 30_000, maxOutput = 1024)
                check(!revision.timedOut && revision.exitCode == 0 && revision.rawOutput.trim() == expectedCommit) {
                    "Pinned source verification failed. The imported folder was not registered."
                }
                // Do not silently lose source trees referenced by upstream gitlinks.
                // Git's protocol allowlist prevents recursive file/ssh/ext/git transport.
                // Only the pinned-source import performs this additional network operation.
                val nested = c.shellRouter.runWorkspace(
                    gitCommand(listOf(
                        "-C", folder.absolutePath,
                        "-c", "protocol.allow=never",
                        "-c", "protocol.https.allow=always",
                        "submodule", "update", "--init", "--recursive", "--jobs=2"
                    )),
                    folderFs, timeoutMs = 600_000, maxOutput = 128_000)
                if (nested.exitCode != 0 || nested.timedOut ||
                    nested.rawOutput.length >= 128_000 || nested.rawStderr.length >= 128_000)
                    throw GitHubOperationFailure(
                        "Pinned nested source checkout",
                        gitHubFailureHint(nested.exitCode, nested.rawStderr + nested.rawOutput)
                    )
                val submoduleStatus = c.shellRouter.runWorkspace(
                    gitCommand(listOf("-C", folder.absolutePath, "submodule", "status", "--recursive")),
                    folderFs, timeoutMs = 60_000, maxOutput = 128_000)
                check(!submoduleStatus.timedOut && submoduleStatus.exitCode == 0 &&
                    submoduleStatus.rawOutput.length < 128_000 &&
                    pinnedSubmodulesMatchGitlinks(submoduleStatus.rawOutput)) {
                    "Nested repositories are missing or do not match their pinned commits. The incomplete project was not registered."
                }
                // Git checkout can leave tiny LFS pointer files instead of the
                // actual assets. Resolve these in each pinned Git repository,
                // or fail closed rather than claiming a complete import.
                if (sourceUsesGitLfs(folder)) {
                    val roots = listOf(folder) + submoduleStatus.rawOutput.lineSequence()
                        .filter { it.isNotBlank() }
                        .map { line ->
                            val relative = line.drop(42).substringBefore(" (")
                            File(folder, relative).canonicalFile.also { nestedRoot ->
                                check(nestedRoot.toPath().startsWith(folder.canonicalFile.toPath())) {
                                    "Nested source path escaped the import workspace."
                                }
                            }
                        }.toList()
                    val lfsVersion = c.shellRouter.runWorkspace(
                        gitCommand(listOf("-C", folder.absolutePath, "lfs", "version")),
                        folderFs, timeoutMs = 30_000, maxOutput = 1_024)
                    check(lfsVersion.exitCode == 0 && !lfsVersion.timedOut) {
                        "This source uses Git LFS. Install Git LFS in the Linux environment and retry; pointer files are not accepted as complete source."
                    }
                    for (repoRoot in roots.distinctBy { it.canonicalPath }) {
                        if (!sourceUsesGitLfs(repoRoot)) continue
                        check(!File(repoRoot, ".lfsconfig").exists()) {
                            "This source includes a custom Git LFS endpoint; review its license and remote configuration before importing."
                        }
                        val remote = c.shellRouter.runWorkspace(
                            gitCommand(listOf("-C", repoRoot.absolutePath, "config", "--get", "remote.origin.url")),
                            folderFs, timeoutMs = 30_000, maxOutput = 2_048)
                        check(remote.exitCode == 0 && !remote.timedOut) {
                            "Unable to validate the Git LFS source URL."
                        }
                        val remoteUrl = gitHubRepositoryUrl(remote.rawOutput.trim())
                        check(remoteUrl.startsWith("https://github.com/")) {
                            "Git LFS for this source must use a verified GitHub HTTPS remote."
                        }
                        val endpoint = remoteUrl.removeSuffix(".git") + ".git/info/lfs"
                        val pull = c.shellRouter.runWorkspace(
                            gitCommand(listOf("-C", repoRoot.absolutePath,
                                "-c", "lfs.url=$endpoint", "lfs", "pull")),
                            folderFs, timeoutMs = 600_000, maxOutput = 128_000)
                        if (pull.exitCode != 0 || pull.timedOut ||
                            pull.rawOutput.length >= 128_000 || pull.rawStderr.length >= 128_000)
                            throw GitHubOperationFailure("Pinned Git LFS download",
                                gitHubFailureHint(pull.exitCode, pull.rawStderr + pull.rawOutput))
                        // A successful pull may still leave pointers due to LFS include/exclude settings.
                        // Require every tracked LFS file in this checkout to be materialized.
                        val lfsListing = c.shellRouter.runWorkspace(
                            gitCommand(listOf("-C", repoRoot.absolutePath, "lfs", "ls-files", "--long")),
                            folderFs, timeoutMs = 60_000, maxOutput = 128_000)
                        check(!lfsListing.timedOut && lfsListing.exitCode == 0 &&
                            lfsListing.rawOutput.length < 128_000 &&
                            lfsCheckoutIsHydrated(lfsListing.rawOutput)) {
                            "Git LFS assets are still pointers or their status could not be verified. The incomplete project was not registered."
                        }
                    }
                }
                // Nested checkout must not move the already verified parent HEAD.
                val finalRevision = c.shellRouter.runWorkspace(
                    gitCommand(listOf("-C", folder.absolutePath, "rev-parse", "--verify", "HEAD")),
                    folderFs, timeoutMs = 30_000, maxOutput = 1024)
                check(!finalRevision.timedOut && finalRevision.exitCode == 0 &&
                    finalRevision.rawOutput.trim() == expectedCommit) {
                    "Pinned parent revision changed during nested checkout."
                }
            }
            currentCoroutineContext().ensureActive()
            // Registration must finish once started, so cancellation cannot
            // leave a registered workspace pointing at a deleted clone.
            withContext(NonCancellable) {
                val project = c.workspace.addShellProject(folder.absolutePath, activate)
                registered = true
                c.workspace.renameProject(project.id, name.take(100))
                onRegistered(project.id)
            }
            "Imported $name into its own workspace."
        } finally {
            if (!registered) withContext(NonCancellable) { folder.deleteRecursively() }
        }
    } }
}
