package com.androidharness.app.integrations

/** A directory of available connection paths, not a claim of an authenticated account. */
enum class IntegrationPath { GITHUB, EMAIL_COMPOSE, DOCUMENT_PICKER, MCP_OR_PLUGIN }

data class IntegrationEntry(val id: String, val title: String, val path: IntegrationPath)

/** Open ecosystem: adding a directory entry never enables or grants access to its remote service. */
object IntegrationCatalog {
    val entries = listOf(
        IntegrationEntry("github", "GitHub", IntegrationPath.GITHUB),
        IntegrationEntry("gmail", "Gmail", IntegrationPath.EMAIL_COMPOSE),
        IntegrationEntry("drive", "Google Drive", IntegrationPath.DOCUMENT_PICKER),
        IntegrationEntry("calendar", "Google Calendar", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("contacts", "Google Contacts", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("canva", "Canva", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("figma", "Figma", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("sharepoint", "Microsoft SharePoint", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("linear", "Linear", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("supabase", "Supabase", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("replit", "Replit", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("heygen", "HeyGen", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("openart", "OpenArt", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("higgsfield", "Higgsfield", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("firecrawl", "Firecrawl", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("context7", "Context7", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("unusual-whales", "Unusual Whales", IntegrationPath.MCP_OR_PLUGIN),
        IntegrationEntry("chatgpt-space", "ChatGPT Space", IntegrationPath.MCP_OR_PLUGIN),
    )

    fun selectedFirst(id: String?): List<IntegrationEntry> {
        if (id.isNullOrBlank() || id == "all") return entries
        val chosen = entries.firstOrNull { it.id == id } ?: return entries
        return listOf(chosen) + entries.filterNot { it.id == id }
    }
}
