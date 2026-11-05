package com.androidharness.app.documents

import android.content.res.Configuration
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.androidharness.app.R
import com.androidharness.app.data.ThemeMode
import com.androidharness.app.ui.chat.ChatListTestActivity
import com.androidharness.app.ui.documents.DocumentStudioScreen
import com.androidharness.app.ui.theme.HarnessTheme
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic document, real native preview and Android system picker. No project/account access. */
@RunWith(AndroidJUnit4::class)
class DocumentStudioPickerDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun nodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
    }
    private fun match(node: AccessibilityNodeInfo, label: String): Boolean =
        node.text?.toString()?.lines()?.any { it.equals(label, true) } == true || node.contentDescription?.toString()?.equals(label, true) == true
    private fun awaitNode(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            nodes().firstOrNull { it.isVisibleToUser && match(it, label) }?.let { return it }
            val scrolling = nodes().firstOrNull { it.isVisibleToUser && it.isScrollable }
            scrolling?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(150)
        }
        error("Missing synthetic document control: $label")
    }
    private fun tap(label: String) {
        var node: AccessibilityNodeInfo? = awaitNode(label)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue(requireNotNull(node).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun fill(label: String, value: String) {
        val labelNode = awaitNode(label)
        var node: AccessibilityNodeInfo? = labelNode
        while (node != null && !node.isEditable) node = node.parent
        if (node == null) node = nodes().firstOrNull { it.isVisibleToUser && it.isEditable && (it.text?.toString()?.contains(label) == true) }
        assertTrue(requireNotNull(node) { "Missing editable field: $label" }.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
        instrumentation.waitForIdleSync()
    }

    @Test fun persianPdfPreviewAndRealPickerSaveReopenTheVerifiedDocument() {
        val target = instrumentation.targetContext
        val fa = target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("fa")); setLayoutDirection(Locale.forLanguageTag("fa"))
        })
        val saved = AtomicReference<Uri?>()
        val fixtureName = "SabaFixture${System.nanoTime()}"
        ActivityScenario.launch(ChatListTestActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity -> activity.setContent {
                    HarnessTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                        CompositionLocalProvider(
                            LocalContext provides fa,
                            LocalLayoutDirection provides LayoutDirection.Rtl,
                            LocalActivityResultRegistryOwner provides activity,
                        ) {
                            Surface { DocumentStudioScreen(onBack = {}, onSaved = { uri, mime, name ->
                                assertEquals("application/pdf", mime)
                                assertTrue(name.startsWith(fixtureName))
                                saved.set(uri)
                            }) }
                        }
                    }
                } }
                fill(fa.getString(R.string.ds_name), fixtureName)
                fill(fa.getString(R.string.ds_content), "گزارش آزمایشی صبا استودیو\nاین سند فقط دادهٔ ساختگی آزمون است.")
                tap(fa.getString(R.string.ds_generate))
                awaitNode(fa.getString(R.string.ds_preview))
                val evidence = File(target.getExternalFilesDir(null), "issue8/document-ui").apply { mkdirs() }
                instrumentation.uiAutomation.takeScreenshot().also { bitmap ->
                    File(evidence, "document-fa-light-preview.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                tap(fa.getString(R.string.ds_save))
                // System DocumentsUI remains in the emulator's English locale.
                nodes().firstOrNull { it.isVisibleToUser && match(it, "Show roots") }?.let {
                    it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    tap("Downloads")
                }
                tap("Save")
                val deadline = SystemClock.uptimeMillis() + 20_000
                while (saved.get() == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
                val uri = requireNotNull(saved.get()) { "No verified URI returned from real picker save" }
                target.contentResolver.openFileDescriptor(uri, "r").use { descriptor ->
                    PdfRenderer(requireNotNull(descriptor)).use { pdf -> assertTrue(pdf.pageCount >= 1) }
                }
                File(evidence, "result.txt").writeText("Persian light native PDF preview and explicit Android picker save passed; returned URI reopened by PdfRenderer. Synthetic emulator fixture only.\n")
            } finally {
                // Delete only the exact unique synthetic output returned by this test.
                saved.get()?.let { DocumentsContract.deleteDocument(target.contentResolver, it) }
            }
        }
    }
}
