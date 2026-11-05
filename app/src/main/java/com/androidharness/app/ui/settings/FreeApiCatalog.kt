package com.androidharness.app.ui.settings

/**
 * Offline, source-linked discovery directory, not a live price/uptime guarantee.
 * Free capacity and available models are determined by the user's own account.
 * Nothing here creates accounts, requests credentials, or submits prompts.
 */
internal enum class FreeApiTier { LIMITED_FREE, TRIAL, LOCAL_NO_KEY, SETUP_REQUIRED, DIRECTORY }

internal data class FreeApiEntry(
    val id: String,
    val title: String,
    val tier: FreeApiTier,
    val officialUrl: String,
    val providerBrand: String? = null,
    val suggestedModel: String? = null,
    val noCardConfirmed: Boolean = false,
) {
    fun connectionPreset(): ProviderBrand? =
        providerBrand?.let { label ->
            ProviderBrands.filterNotNull().firstOrNull { it.label == label }
        }?.let { brand ->
            if (suggestedModel == null) brand else brand.copy(suggestedModel = suggestedModel)
        }
}

/**
 * The bank only lists a service as free when an official source documents a
 * limited free allocation; no service is marked unlimited.
 * Trial entries can expire. Directory entries are research resources, not API providers.
 * Verified against the linked provider documentation on 2026-11-05.
 */
internal object FreeApiCatalog {
    val entries: List<FreeApiEntry> = listOf(
        FreeApiEntry("gemini", "Google Gemini API", FreeApiTier.LIMITED_FREE,
            "https://ai.google.dev/gemini-api/docs/pricing", "Gemini", "gemini-2.5-flash", true),
        FreeApiEntry("groq", "Groq Cloud", FreeApiTier.LIMITED_FREE,
            "https://console.groq.com/docs/rate-limits", "Groq", "openai/gpt-oss-20b"),
        FreeApiEntry("openrouter", "OpenRouter Free Models", FreeApiTier.LIMITED_FREE,
            "https://openrouter.ai/collections/free-models", "OpenRouter", "openrouter/free"),
        FreeApiEntry("mistral", "Mistral AI", FreeApiTier.LIMITED_FREE,
            "https://docs.mistral.ai/getting-started/quickstarts/studio/activate-and-generate-api-key",
            "Mistral", noCardConfirmed = true),
        FreeApiEntry("cohere", "Cohere Trial", FreeApiTier.TRIAL,
            "https://docs.cohere.com/", "Cohere"),
        FreeApiEntry("cloudflare", "Cloudflare Workers AI", FreeApiTier.SETUP_REQUIRED,
            "https://developers.cloudflare.com/workers-ai/platform/pricing/"),
        FreeApiEntry("ollama", "Ollama (on-device server)", FreeApiTier.LOCAL_NO_KEY,
            "https://docs.ollama.com/", "Ollama"),
        FreeApiEntry("public-apis", "Public APIs Directory", FreeApiTier.DIRECTORY,
            "https://github.com/public-apis/public-apis"),
        FreeApiEntry("free-for-dev", "free-for.dev", FreeApiTier.DIRECTORY,
            "https://free-for.dev/"),
    )

    fun search(query: String, onlyConnectable: Boolean = false): List<FreeApiEntry> {
        val q = query.trim()
        return entries.filter { (!onlyConnectable || it.connectionPreset() != null) &&
            (q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.id.contains(q, ignoreCase = true)) }
    }
}
