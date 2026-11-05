package com.androidharness.app.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SabaFirstRunLocaleTest {
    @Test fun freshInstallWithoutOverrideDefaultsToPersian() {
        assertTrue(SabaFirstRunLocale.shouldSelectPersian(
            initialized = false, firstInstallTime = 100L,
            lastUpdateTime = 100L, hasAppLocaleOverride = false,
        ))
    }

    @Test fun upgradesNeverReplaceExistingLanguagePreferences() {
        assertFalse(SabaFirstRunLocale.shouldSelectPersian(
            initialized = false, firstInstallTime = 100L,
            lastUpdateTime = 200L, hasAppLocaleOverride = false,
        ))
    }

    @Test fun explicitLanguageAndSystemChoicesRemainStable() {
        assertFalse(SabaFirstRunLocale.shouldSelectPersian(
            initialized = false, firstInstallTime = 100L,
            lastUpdateTime = 100L, hasAppLocaleOverride = true,
        ))
        assertFalse(SabaFirstRunLocale.shouldSelectPersian(
            initialized = true, firstInstallTime = 100L,
            lastUpdateTime = 100L, hasAppLocaleOverride = false,
        ))
    }

    @Test fun missingInstallMetadataNeverChangesLocale() {
        assertFalse(SabaFirstRunLocale.shouldSelectPersian(
            initialized = false, firstInstallTime = 0L,
            lastUpdateTime = 0L, hasAppLocaleOverride = false,
        ))
    }
}
