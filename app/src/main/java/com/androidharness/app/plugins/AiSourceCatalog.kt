package com.androidharness.app.plugins

/**
 * Exact-source catalogue for the AI integration suite.
 *
 * References to pinned, separate upstream applications. Registration is NOT
 * installation, runtime integration or permission to relicense source code.
 * The unmodified complete sources are submodules under third_party/ai-sources.
 */
data class AiSourceProject(
    val id: String,
    val repository: String,
    val pinnedCommit: String,
    val license: String,
    val descriptionFa: String,
    val descriptionEn: String,
) {
    val canImportAsWorkspace: Boolean get() = license != "UNVERIFIED" && !license.startsWith("RESTRICTED")
    val pinnedSourceUrl: String get() = "https://github.com/$repository/tree/$pinnedCommit"
}

object AiSourceCatalog {
    val projects: List<AiSourceProject> = listOf(
        AiSourceProject("lyra-code", "lyracode-app/Lyra-Code", "90fde028c35f953fcab36d4460fa93852eacdd00", "AGPL-3.0", "محیط توسعه اندروید با عامل کدنویسی، Linux و Termux", "Android coding IDE with agent and Linux/Termux"),
        AiSourceProject("and-code", "yuga-hashimoto/and-code", "b0be8ad5e2c9a1199a6be9fb79319579bb2d25df", "MIT", "رابط اندرویدی برای اجرای عامل‌های برنامه‌نویسی", "Android GUI for local coding agents"),
        AiSourceProject("termfold", "Bibarud/TermFold", "a9f678d794a9f9942da923161d6fe996b89b935f", "Apache-2.0", "محیط Debian و عامل‌های کدنویسی روی گوشی", "Debian environment and coding agents on Android"),
        AiSourceProject("androidassistant", "xnylh502/AndroidAssistant", "fdc6274d60a0920cb84aba6f2cf30df565b522dd", "MIT", "دستیار هوشمند فایل، شِل و ابزارهای توسعه", "AI assistant for files, shell and development tools"),
        AiSourceProject("mobile-harness", "techjarves/Mobile-Harness", "ae299adc7ecef5d9ed63f46e3dbd9af30818968d", "MIT", "محیط کدنویسی موبایل با بررسی تغییرات و پیش‌نمایش", "Mobile coding IDE with diffs and preview"),
        AiSourceProject("antigem", "Santoshkurmi/antigem", "78cbb7aa5267d23a7077986d45ab9ffe565641bc", "GPL-3.0", "محیط توسعه مبتنی بر Antigravity و Claude", "Development UI built around Antigravity and Claude"),
        AiSourceProject("syntac", "DraxonV1/Syntac", "ce5180a51a7ed63bc2dfa1db43f0d00a3abe57c9", "UNVERIFIED", "عامل محلی برنامه‌نویسی در اندروید", "Local coding agent for Android"),
        AiSourceProject("pocket-pi", "CelestialCreator/pocket-pi", "9cf4660dfcbaf49381dc828c2a4144d660d385fc", "MIT", "عامل Pi همراه با محیط اندروید", "Pi coding agent for Android"),
        AiSourceProject("zafiro", "niki914/zafiro", "c9b5b6a0d7f6e629e3d5983b32def4aa3eca25c3", "MIT", "عامل کنترل گوشی با Skills و MCP", "Phone control agent with Skills and MCP"),
        AiSourceProject("androidagent", "mateuszsury/androidagent", "447ae6508d05c7531379afa9c3a29919addeb275", "Apache-2.0", "اجرای Codex و Claude همراه با ترمینال", "Codex and Claude with embedded terminal"),
        AiSourceProject("mobilekinetic-agent", "Binary-Kinetics/mobileKinetic-Agent", "3f1a5771b586b0c149f25d37b7aba0651c98a8dc", "GPL-3.0", "بستر ساخت عامل با ابزارهای MCP و Python", "Agent platform with MCP and Python"),
        AiSourceProject("openclaude-android", "automationkit/openclaude-android", "5866e38523c27b27382b7277cb9b30f9f2d42c37", "RESTRICTED-PROPRIETARY-DERIVED", "هشدار: بخشی از سورس از کد مالکیتی بدون مجوز انتشار مشتق شده است", "Warning: proprietary-derived code is not authorized for redistribution"),
        AiSourceProject("droid-harness", "eibragaa/droid-harness", "a3438fddf3ec6dc319ebce5858906f9115ba6353", "MIT", "اجرای عامل‌های کدنویسی روی گوشی‌های Snapdragon", "Coding agents on Snapdragon Android devices"),
    )
}
