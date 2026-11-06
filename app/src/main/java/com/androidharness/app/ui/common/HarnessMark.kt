package com.androidharness.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.androidharness.app.R

/**
 * Saba Studio identity: reuse the EXACT vector used by the launcher and splash.
 * No terminal-prompt badge and no theme-dependent black/white replacement.
 */
@Composable
fun SabaBrandMark(size: Dp, modifier: Modifier = Modifier) {
    Surface(
        color = colorResource(R.color.ic_launcher_background),
        shape = RoundedCornerShape(size * 0.28f),
        modifier = modifier.size(size),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null, // The adjacent text identifies the app.
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

/** Compatibility wrapper for older call sites. New screens should use SabaBrandMark. */
@Composable
fun HarnessMark(size: Dp, modifier: Modifier = Modifier) = SabaBrandMark(size, modifier)

/**
 * Source-compatible legacy ImageVector alias. Uses the same colored Saba glyph,
 * never the old >_ prompt. Prefer the XML-backed SabaBrandMark for actual UI.
 */
@Deprecated("Use SabaBrandMark so in-app identity exactly matches launcher/splash")
val PromptMark: ImageVector = ImageVector.Builder(
    name = "SabaStudioMark",
    defaultWidth = 108.dp,
    defaultHeight = 108.dp,
    viewportWidth = 108f,
    viewportHeight = 108f,
).apply {
    path(fill = SolidColor(Color(0xFF164569))) {
        moveTo(54f, 19f)
        curveTo(73.3f, 19f, 89f, 34.7f, 89f, 54f)
        curveTo(89f, 73.3f, 73.3f, 89f, 54f, 89f)
        curveTo(34.7f, 89f, 19f, 73.3f, 19f, 54f)
        curveTo(19f, 34.7f, 34.7f, 19f, 54f, 19f)
        close()
    }
    path(fill = SolidColor(Color(0xFF40D5F2))) {
        moveTo(28f, 45f)
        lineTo(44f, 29f)
        lineTo(66f, 29f)
        lineTo(80f, 43f)
        lineTo(71f, 52f)
        lineTo(61f, 41f)
        lineTo(48f, 41f)
        lineTo(38f, 52f)
        close()
    }
    path(fill = SolidColor(Color(0xFFF6FCFF))) {
        moveTo(80f, 63f)
        lineTo(64f, 79f)
        lineTo(41f, 79f)
        lineTo(28f, 66f)
        lineTo(37f, 57f)
        lineTo(46f, 67f)
        lineTo(60f, 67f)
        lineTo(70f, 56f)
        close()
    }
    path(fill = SolidColor(Color(0xFF81F0CA))) {
        moveTo(70f, 25f)
        lineTo(74f, 30f)
        lineTo(79f, 25f)
        lineTo(74f, 20f)
        close()
    }
}.build()
