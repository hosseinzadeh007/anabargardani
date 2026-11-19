package com.hesabgara.anbargardan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object Export {
    private const val EPS = 1e-9
    private const val LRM = "\u200E"
    private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    fun status(d: Double) = if (d < -EPS) "کسری" else if (d > EPS) "اضافه" else "مطابق"
    private fun kind(d: Double) = if (d < -EPS) -1 else if (d > EPS) 1 else 0
    private fun dir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }
    private fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    private fun makeXlsx(ctx: Context, rows: List<Rep>): File {
        val data = ArrayList<List<Any?>>()
        data.add(listOf("کد کالا", "نام کالا", "شکل دارویی", "دسته", "موجودی سیستم", "شمارش اول", "شمارش مجدد", "موجودی نهایی", "مغایرت", "وضعیت"))
        rows.forEach { r ->
            val i = r.item
            data.add(listOf(i.code, i.name, i.form, i.category, i.system, i.first, i.second, r.qty, r.diff, status(r.diff)))
        }
        val f = File(dir(ctx), "anbargardani_report.xlsx")
        f.outputStream().use { Excel.write(it, data) }
        return f
    }

    private fun makePdf(ctx: Context, rows: List<Rep>, scope: String): File {
        val f = File(dir(ctx), "anbargardani_report.pdf")
        val body = rows.map { r ->
            val i = r.item
            listOf(
                LRM + i.code, i.name + (if (i.form.isNotEmpty()) " (${i.form})" else ""), LRM + faN(i.system),
                LRM + (i.first?.let { faN(it) } ?: "-"), LRM + (i.second?.let { faN(it) } ?: "-"),
                LRM + (if (r.diff > 0) "+" else "") + faN(r.diff), status(r.diff)
            ) to kind(r.diff)
        }
        PdfOut.write(
            f, "گزارش انبارگردانی", "$scope  |  ${Jalali.dateTime()}",
            listOf(.15f, .27f, .11f, .11f, .10f, .12f, .14f),
            listOf("کد", "نام کالا", "سیستم", "شمارش اول", "مجدد", "اختلاف", "وضعیت"), body
        )
        return f
    }

    fun shareFiles(ctx: Context, rows: List<Rep>, scope: String, xl: Boolean, pd: Boolean) {
        val files = ArrayList<Pair<File, String>>()
        if (xl) files.add(makeXlsx(ctx, rows) to XLSX)
        if (pd) files.add(makePdf(ctx, rows, scope) to "application/pdf")
        if (files.isEmpty()) return
        val i = if (files.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = files[0].second
            putExtra(Intent.EXTRA_STREAM, uri(ctx, files[0].first))
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uri(ctx, it.first) }))
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "اشتراک‌گذاری گزارش"))
    }

    /** Sends the data file through the Android share sheet (SHAREit, Bluetooth, Quick Share, messengers...). */
    fun shareBackup(ctx: Context, json: String) {
        val f = File(dir(ctx), "anbargardani-backup.anbar")
        f.writeText(json)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri(ctx, f))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "ارسال اطلاعات به موبایل دیگر"))
    }

    fun print(ctx: Context, rows: List<Rep>, scope: String) {
        val f = makePdf(ctx, rows, scope)
        val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print("گزارش انبارگردانی", FileAdapter(f), null)
    }

    private class FileAdapter(private val f: File) : PrintDocumentAdapter() {
        override fun onLayout(
            old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?,
            cb: LayoutResultCallback, extras: Bundle?
        ) {
            if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
            cb.onLayoutFinished(
                PrintDocumentInfo.Builder(f.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true
            )
        }

        override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
            try {
                FileInputStream(f).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                cb.onWriteFailed(e.message)
            }
        }
    }
}
