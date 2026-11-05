package com.androidharness.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * Use the platform's Arabic/Persian-capable sans-serif for RTL prose.
 * The Latin-only local Google Sans Flex cannot shape all Persian glyphs,
 * and positive letter spacing splits connected-script letterforms.
 *
 * All 28 Material 3 roles keep their approved sizes and hierarchy. Explicit
 * monospace editor and code styles remain unchanged.
 */
internal val PersianTypography = Typography(
    displayLarge = HarnessTypography.displayLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    displayMedium = HarnessTypography.displayMedium.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    displaySmall = HarnessTypography.displaySmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    headlineLarge = HarnessTypography.headlineLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    headlineMedium = HarnessTypography.headlineMedium.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    headlineSmall = HarnessTypography.headlineSmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    titleLarge = HarnessTypography.titleLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    titleMedium = HarnessTypography.titleMedium.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    titleSmall = HarnessTypography.titleSmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    bodyLarge = HarnessTypography.bodyLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    bodyMedium = HarnessTypography.bodyMedium.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    bodySmall = HarnessTypography.bodySmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    labelLarge = HarnessTypography.labelLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    labelMedium = HarnessTypography.labelMedium.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    labelSmall = HarnessTypography.labelSmall.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    displaySmallEmphasized = HarnessTypography.displaySmallEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    headlineLargeEmphasized = HarnessTypography.headlineLargeEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    headlineMediumEmphasized = HarnessTypography.headlineMediumEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    headlineSmallEmphasized = HarnessTypography.headlineSmallEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    titleLargeEmphasized = HarnessTypography.titleLargeEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    titleMediumEmphasized = HarnessTypography.titleMediumEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    titleSmallEmphasized = HarnessTypography.titleSmallEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    bodyLargeEmphasized = HarnessTypography.bodyLargeEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    bodyMediumEmphasized = HarnessTypography.bodyMediumEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    bodySmallEmphasized = HarnessTypography.bodySmallEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    labelLargeEmphasized = HarnessTypography.labelLargeEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    labelMediumEmphasized = HarnessTypography.labelMediumEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
    labelSmallEmphasized = HarnessTypography.labelSmallEmphasized.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
)
