package com.androidharness.app.plugins

/**
 * Bounded shell bridge to independently installed, user-owned AI agent CLIs.
 *
 * This is NOT a bundled copy of the referenced Android applications. It requires
 * the selected CLI to be installed and authenticated in the current workspace's
 * Linux/SSH environment. It never installs a package or injects an API key.
 */
data class AiCliProfile(
    val id: String,
    val executable: String,
    val label: String,
    val supportsReadOnly: Boolean,
)

object AiRuntimeBridge {
    val profiles = listOf(
        AiCliProfile("codex", "codex", "Codex CLI", true),
        AiCliProfile("claude", "claude", "Claude Code", true),
        AiCliProfile("opencode", "opencode", "OpenCode", false),
        AiCliProfile("gemini", "gemini", "Gemini CLI", true),
        AiCliProfile("pi", "pi", "Pi Agent", false),
        AiCliProfile("qwen", "qwen", "Qwen Code", false),
        AiCliProfile("aider", "aider", "Aider", true),
    )

    private fun profile(id: String): AiCliProfile =
        profiles.singleOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown AI CLI: $id")

    /** Shell-quote a single argument, including embedded single quotes. */
    fun quoteArgument(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /** Never evaluate user-controlled shell input, even for availability checks. */
    fun checkCommand(id: String): String =
        "command -v " + profile(id).executable + " >/dev/null 2>&1"

    /**
     * Opt-in version probe for a user-installed CLI. The executable comes only
     * from the fixed allowlist; no prompt, account, URL or token is interpolated.
     * A version string is not proof of authentication or runtime compatibility.
     */
    fun versionCommand(id: String): String = profile(id).executable + " --version"

    private val versionPattern = Regex(
        """(?<![A-Za-z0-9])v?([0-9]{1,3}\.[0-9]{1,3}(?:\.[0-9]{1,3})?(?:[-+][A-Za-z0-9.]{1,24})?)(?![A-Za-z0-9])"""
    )

    /**
     * Return only a bounded numeric version, never arbitrary output from a
     * third-party executable (which could include credentials or ANSI escapes).
     */
    fun parseVersion(output: String): String? {
        val firstLine = output.lineSequence().firstOrNull()?.take(240).orEmpty()
        return versionPattern.find(firstLine)?.groupValues?.get(1)
    }

    /** Only a UUID is accepted; never interpret paths, options or shell fragments as a session. */
    fun isValidSessionId(value: String): Boolean =
        value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))

    /** Provider-specific, strict identifiers. No session discovery or cross-workspace lookup. */
    fun isValidSessionForAgent(id: String, value: String): Boolean = when (id) {
        "claude", "gemini" -> isValidSessionId(value)
        "opencode" -> value.matches(Regex("ses_[a-zA-Z0-9]{6,64}"))
        else -> false
    }

    /**
     * The selected executable is a fixed allowlisted command. Only the prompt
     * is dynamic, passed as ONE POSIX-quoted argument prefixed with explanatory
     * text so leading dashes cannot be parsed as CLI options.
     */
    fun executionCommand(id: String, prompt: String, allowWorkspaceEdits: Boolean, sessionId: String? = null): String {
        val cli = profile(id)
        require(sessionId == null || isValidSessionForAgent(cli.id, sessionId)) {
            "Session ID is not valid for the selected CLI. Use an existing Claude/Gemini UUID or OpenCode ses_ ID."
        }
        val message = prompt.trim()
        require(message.isNotEmpty() && message.length <= 2400) { "Enter 1-2400 characters." }
        require(message.none { it == '\u0000' || it == '\r' || (it.isISOControl() && it != '\n' && it != '\t') }) {
            "Prompt contains unsupported control characters."
        }
        require(allowWorkspaceEdits || cli.supportsReadOnly) {
            "This agent has no verified read-only mode; approve workspace editing explicitly."
        }
        val prefix = when (cli.id) {
            "codex" -> if (allowWorkspaceEdits) "codex exec --sandbox workspace-write" else "codex exec --sandbox read-only"
            "claude" -> (if (allowWorkspaceEdits) "claude -p --permission-mode acceptEdits" else "claude -p --permission-mode plan") +
                (if (sessionId == null) "" else " --resume " + quoteArgument(sessionId))
            "opencode" -> "opencode run" +
                (if (sessionId == null) "" else " --session " + quoteArgument(sessionId))
            "gemini" -> "gemini" +
                (if (sessionId == null) "" else " --resume " + quoteArgument(sessionId)) +
                (if (allowWorkspaceEdits) " -p" else " --approval-mode plan -p")
            "pi" -> "pi -p"
            "qwen" -> "qwen -p"
            "aider" -> if (allowWorkspaceEdits) "aider --message" else "aider --dry-run --message"
            else -> error("Unknown CLI")
        }
        return prefix + " " + quoteArgument("User request: " + message)
    }
}
