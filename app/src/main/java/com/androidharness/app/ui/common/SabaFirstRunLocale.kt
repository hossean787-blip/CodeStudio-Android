package com.androidharness.app.ui.common

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Saba Studio defaults to Persian for brand-new installs.
 *
 * Upgrades and existing per-app language choices are never overwritten.
 * A persisted one-shot marker also preserves an explicit "Follow system"
 * choice on subsequent launches.
 */
internal object SabaFirstRunLocale {
    internal fun shouldSelectPersian(
        initialized: Boolean,
        firstInstallTime: Long,
        lastUpdateTime: Long,
        hasAppLocaleOverride: Boolean,
    ): Boolean = !initialized && !hasAppLocaleOverride &&
        firstInstallTime > 0L && firstInstallTime == lastUpdateTime

    @Suppress("DEPRECATION")
    fun applyIfNeeded(activity: AppCompatActivity) {
        val preferences = activity.getSharedPreferences("saba_studio_locale", Context.MODE_PRIVATE)
        val initialized = preferences.getBoolean("initialized", false)
        if (initialized) return

        val info = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0)
        }.getOrNull()
        val usePersian = shouldSelectPersian(
            initialized = initialized,
            firstInstallTime = info?.firstInstallTime ?: 0L,
            lastUpdateTime = info?.lastUpdateTime ?: 0L,
            hasAppLocaleOverride = !AppCompatDelegate.getApplicationLocales().isEmpty,
        )
        // Persist before triggering AppCompat's potential Activity recreation.
        preferences.edit().putBoolean("initialized", true).apply()
        if (usePersian) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa"))
        }
    }
}
