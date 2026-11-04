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
        AiCliProfile("gemini", "gemini", "Gemini CLI", false),
        AiCliProfile("pi", "pi", "Pi Agent", false),
        AiCliProfile("qwen", "qwen", "Qwen Code", false),
        AiCliProfile("aider", "aider", "Aider", false),
    )

    private fun profile(id: String): AiCliProfile =
        profiles.singleOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown AI CLI: $id")

    /** Shell-quote a single argument, including embedded single quotes. */
    fun quoteArgument(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /** Never evaluate user-controlled shell input, even for availability checks. */
    fun checkCommand(id: String): String =
        "command -v " + profile(id).executable + " >/dev/null 2>&1"

    /**
     * The selected executable is a fixed allowlisted command. Only the prompt
     * is dynamic, passed as ONE POSIX-quoted argument prefixed with explanatory
     * text so leading dashes cannot be parsed as CLI options.
     */
    fun executionCommand(id: String, prompt: String, allowWorkspaceEdits: Boolean): String {
        val cli = profile(id)
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
            "claude" -> if (allowWorkspaceEdits) "claude -p --permission-mode acceptEdits" else "claude -p --permission-mode plan"
            "opencode" -> "opencode run"
            "gemini" -> "gemini -p"
            "pi" -> "pi -p"
            "qwen" -> "qwen -p"
            "aider" -> "aider --message"
            else -> error("Unknown CLI")
        }
        return prefix + " " + quoteArgument("User request: " + message)
    }
}
