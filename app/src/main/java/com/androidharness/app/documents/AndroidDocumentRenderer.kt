package com.androidharness.app.documents

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

/** Native shaping and rasterization; never WebView, shell, desktop conversion or network. */
object AndroidDocumentRenderer {
    fun encode(spec: DocumentSpec, checkCancelled: () -> Unit = {}): ByteArray {
        DocumentEncoder.validate(spec)
        checkCancelled()
        val bytes = when (spec.format) {
            "pdf" -> pdf(spec, checkCancelled)
            "png" -> png(spec, checkCancelled)
            else -> DocumentEncoder.encode(spec)
        }
        checkCancelled()
        require(bytes.size <= DocumentEncoder.MAX_OUTPUT) { "Output too large" }
        return bytes
    }
    private fun layout(text: String, width: Int, size: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; color = Color.BLACK
        }, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).setIncludePad(true).build()

    private fun pdf(s: DocumentSpec, check: () -> Unit): ByteArray {
        val text = listOf(s.title, s.text).filter { it.isNotEmpty() }.joinToString("\n\n")
        val layout = layout(text, 499, 13f)
        // Bound pages before creating anything; no maxLines/ellipsis or hidden truncation.
        val pages = mutableListOf<Pair<Int, Int>>()
        var start = 0
        while (start < layout.lineCount) {
            check()
            val y = layout.getLineTop(start)
            var end = start
            while (end < layout.lineCount && layout.getLineBottom(end) - y <= 746) end++
            require(end > start) { "A PDF line exceeds page bounds" }
            pages += start to end
            require(pages.size <= 100) { "PDF exceeds 100 pages; split the document" }
            start = end
        }
        val out = BoundedDocumentOutput()
        val doc = PdfDocument()
        var documentFailure: Throwable? = null
        try {
            pages.forEachIndexed { index, (from, to) ->
                check()
                val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                var pageFailure: Throwable? = null
                try {
                    check()
                    val canvas = page.canvas
                    // Each page owns a fresh canvas; no saved canvas state needs cleanup.
                    canvas.translate(48f, 48f)
                    canvas.clipRect(0, 0, 499, layout.getLineTop(to) - layout.getLineTop(from))
                    canvas.translate(0f, -layout.getLineTop(from).toFloat())
                    layout.draw(canvas)
                    check()
                } catch (failure: Throwable) {
                    pageFailure = failure
                    throw failure
                } finally {
                    try {
                        doc.finishPage(page)
                    } catch (cleanupFailure: Throwable) {
                        val original = pageFailure
                        if (original != null) original.addSuppressed(cleanupFailure) else throw cleanupFailure
                    }
                }
            }
            check(); doc.writeTo(out)
        } catch (failure: Throwable) {
            documentFailure = failure
            throw failure
        } finally {
            try {
                doc.close()
            } catch (cleanupFailure: Throwable) {
                val original = documentFailure
                if (original != null) original.addSuppressed(cleanupFailure) else throw cleanupFailure
            }
        }
        return out.toByteArray()
    }
    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, width: Int, size: Float, maxHeight: Int) {
        val textLayout = layout(text, width, size)
        require(textLayout.height <= maxHeight) { "Chart labels/title do not fit; shorten labels or increase dimensions" }
        canvas.save(); canvas.translate(x, y); textLayout.draw(canvas); canvas.restore()
    }
    private fun png(s: DocumentSpec, check: () -> Unit): ByteArray {
        val bitmap = Bitmap.createBitmap(s.width, s.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            drawText(canvas, s.title, 30f, 10f, s.width - 60, 18f, 44)
            val left = 70f; val top = 60f; val plotW = s.width - 100f; val plotH = s.height - 150f
            val step = plotW / s.values.size
            require(step >= 18) { "Too many chart bars for width" }
            val max = s.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(37, 99, 235) }
            s.values.forEachIndexed { i, value ->
                check()
                val x = left + i * step
                val h = (plotH * value / max).toFloat()
                canvas.drawRect(x, top + plotH - h, x + step * 0.7f, top + plotH, paint)
                drawText(canvas, s.labels[i], x, top + plotH + 8, (step - 2).toInt(), 11f, 76)
            }
            val out = BoundedDocumentOutput()
            require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG compression failed" }
            return out.toByteArray()
        } finally { bitmap.recycle() }
    }
}
