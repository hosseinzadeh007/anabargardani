package com.hesabgara.anbargardan

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.abs

fun fa(s: String): String = buildString { for (c in s) append(if (c in '0'..'9') '۰' + (c - '0') else c) }
fun faN(d: Double): String = fa(fmt(d))
fun faI(n: Int): String = fa(n.toString())
fun signed(d: Double): String =
    if (abs(d) < 1e-9) "—" else "\u200E" + (if (d > 0) "+" else "−") + faN(abs(d))

class ReportData(val rows: List<Rep>, val shortSum: Double, val surSum: Double)

fun scopeText(cat: String?, form: String?) = "دسته: ${cat ?: "همه"}  |  شکل دارویی: ${form ?: "همه"}"

/** mode: 0 = all counted, 1 = shortages, 2 = surpluses. */
fun buildReport(store: Store, cat: String?, form: String?, zero: Boolean, mode: Int): ReportData =
    buildReportFrom(store.items, cat, form, zero, mode)

fun buildReportFrom(items: List<Item>, cat: String?, form: String?, zero: Boolean, mode: Int): ReportData {
    val all = items.filter { (cat == null || it.category == cat) && (form == null || it.form == form) }.mapNotNull { i ->
        val q = i.finalQty ?: (if (zero) 0.0 else null)
        if (q == null) null else Rep(i, q, q - i.system)
    }
    val rows = all.filter { when (mode) { 1 -> it.diff < -1e-9; 2 -> it.diff > 1e-9; else -> true } }
    var sh = 0.0; var su = 0.0
    for (r in all) { if (r.diff < 0) sh -= r.diff; if (r.diff > 0) su += r.diff }
    return ReportData(rows, sh, su)
}

fun formIcon(form: String): ImageVector = when {
    form.contains("قرص") || form.contains("کپسول") -> Icons.Outlined.Medication
    form.contains("آمپول") || form.contains("ویال") -> Icons.Outlined.Vaccines
    form.contains("قطره") -> Icons.Outlined.WaterDrop
    form.contains("شربت") || form.contains("محلول") -> Icons.Outlined.Science
    form.contains("کرم") || form.contains("پماد") || form.contains("ژل") -> Icons.Outlined.Healing
    form.contains("اسپری") -> Icons.Outlined.Sanitizer
    else -> Icons.Outlined.Inventory2
}
