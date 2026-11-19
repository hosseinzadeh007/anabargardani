package com.hesabgara.anbargardan

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import java.io.File

object PdfOut {
    private const val W = 595
    private const val H = 842
    private const val M = 28
    private const val RH = 28f

    private fun layout(text: String, p: TextPaint, w: Int) =
        StaticLayout.Builder.obtain(text, 0, text.length, p, w)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build()

    /** rows: cells + kind (-1 shortage, 0 match, 1 surplus). Columns are laid out right-to-left. */
    fun write(file: File, title: String, sub: String, fr: List<Float>, header: List<String>, rows: List<Pair<List<String>, Int>>) {
        val doc = PdfDocument()
        val normal = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f }
        val bold = TextPaint(normal).apply { isFakeBoldText = true }
        val big = TextPaint(bold).apply { textSize = 16f }
        val red = TextPaint(normal).apply { color = 0xFFC62828.toInt() }
        val green = TextPaint(normal).apply { color = 0xFF2E7D32.toInt() }
        val shade = Paint().apply { color = 0xFFE0E0E0.toInt() }
        val cw = fr.map { it * (W - 2 * M) }
        var no = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            no++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, no).create())
            y = M.toFloat()
        }
        fun drawText(t: String, p: TextPaint, x: Float, w: Int) {
            val cv: Canvas = page!!.canvas
            val l = layout(t, p, w)
            cv.save(); cv.translate(x, y + 4); l.draw(cv); cv.restore()
        }
        fun drawRow(cells: List<String>, p: TextPaint, bg: Boolean) {
            if (bg) page!!.canvas.drawRect(M.toFloat(), y, (W - M).toFloat(), y + RH, shade)
            var x = (W - M).toFloat()
            cells.forEachIndexed { i, t -> x -= cw[i]; drawText(t, p, x + 3, (cw[i] - 6).toInt()) }
            y += RH
        }

        newPage()
        drawText(title, big, M.toFloat(), W - 2 * M); y += 26f
        drawText(sub, normal, M.toFloat(), W - 2 * M); y += 22f
        drawRow(header, bold, true)
        for ((cells, kind) in rows) {
            if (y > H - M - RH) { newPage(); drawRow(header, bold, true) }
            drawRow(cells, when (kind) { -1 -> red; 1 -> green; else -> normal }, false)
        }
        page?.let { doc.finishPage(it) }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
