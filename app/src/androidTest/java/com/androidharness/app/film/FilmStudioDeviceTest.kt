package com.androidharness.app.film

import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidharness.app.R
import com.androidharness.app.data.ThemeMode
import com.androidharness.app.ui.chat.ChatListTestActivity
import com.androidharness.app.ui.film.FilmStudioScreen
import com.androidharness.app.ui.theme.HarnessTheme
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android MP4 export fixture and Portuguese? No, UI tested in Persian. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class FilmStudioDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun filmStudioOpensInPersianAndProducesInspectableScreenshot() {
        val context = instrumentation.targetContext
        val fa = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("fa"))
            setLayoutDirection(Locale.forLanguageTag("fa"))
        })
        ActivityScenario.launch(ChatListTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HarnessTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                        Surface {
                            androidx.compose.runtime.CompositionLocalProvider(
                                androidx.compose.ui.platform.LocalContext provides fa,
                                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl,
                            ) { FilmStudioScreen(onBack = {}) }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            val screen = instrumentation.uiAutomation.rootInActiveWindow
            fun text(node: android.view.accessibility.AccessibilityNodeInfo?): String =
                if (node == null) "" else (node.text?.toString().orEmpty() + (0 until node.childCount).joinToString("") { text(node.getChild(it)) })
            val visible = text(screen)
            assertTrue(visible.contains(fa.getString(R.string.film_title)))
            assertTrue(visible.contains(fa.getString(R.string.film_choose_video)))
            val folder = File(context.getExternalFilesDir(null), "issue8/film").apply { mkdirs() }
            instrumentation.uiAutomation.takeScreenshot().let { image ->
                File(folder, "film-studio-fa.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
        }
    }

    @Test fun actualMedia3ExportCreatesReadableTrimmedMp4() {
        val context = instrumentation.targetContext
        val folder = File(context.getExternalFilesDir(null), "issue8/film").apply { mkdirs() }
        val input = File(context.cacheDir, "original-film-fixture.mp4")
        val output = File(folder, "media3-trimmed-real.mp4")
        input.writeBytes(Base64.decode(FIXTURE_BASE64, Base64.DEFAULT))
        output.delete()
        val complete = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val transformed = AtomicReference<Transformer?>(null)
        instrumentation.runOnMainSync {
            try {
                val item = MediaItem.Builder().setUri(Uri.fromFile(input))
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(0L).setEndPositionMs(1_000L).build()).build()
                transformed.set(Transformer.Builder(context).addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        complete.countDown()
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        failure.set(exportException.message ?: exportException.javaClass.simpleName)
                        complete.countDown()
                    }
                }).build().also { it.start(item, output.absolutePath) })
            } catch (error: Exception) {
                failure.set(error.message ?: error.javaClass.simpleName)
                complete.countDown()
            }
        }
        val finished = complete.await(65, TimeUnit.SECONDS)
        if (!finished) instrumentation.runOnMainSync { transformed.get()?.cancel() }
        assertTrue("Media3 export timeout", finished)
        assertNull("Real fixture export failed: ${failure.get()}", failure.get())
        assertTrue("MP4 output missing or empty", output.isFile && output.length() > 0)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(output.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            assertNotNull("Export must reopen in Android MediaMetadataRetriever", duration)
            assertTrue("Expected trimmed length under 2s, got $duration", requireNotNull(duration) in 200L..2_000L)
            assertNotNull("Must retain the video codec", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
        } finally {
            retriever.release()
            input.delete()
        }
    }

    companion object {
        // Original flat-colour H.264 baseline fixture generated with ffmpeg; no copyrighted user content.
        private const val FIXTURE_BASE64 = "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"
    }
}
