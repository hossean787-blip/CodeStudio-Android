package com.androidharness.app.data

@kotlinx.serialization.Serializable
enum class ThemePalette {
    BLUE, OCEAN, INDIGO, LAVENDER, ORCHID, ROSE, CORAL, AMBER, LIME, FOREST, TEAL, SLATE, NEON;

    companion object {
        fun fromStored(value: String?): ThemePalette = if (value == null) NEON else entries.firstOrNull { it.name == value } ?: BLUE
    }
}
