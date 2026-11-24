package com.hesabgara.anbargardan

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.Executors

data class Item(
    val code: String,
    val name: String,
    val system: Double,
    val first: Double? = null,
    val second: Double? = null,
    val category: String = "",
    val form: String = "",
    /** Several barcodes per item are allowed; they are separate from the drug code. */
    val barcodes: List<String> = emptyList(),
    /** Stable internal key (never changes when code/name are edited). */
    val id: String = "",
    /** true = added by hand (kept when a new Excel file is imported). */
    val manual: Boolean = false
) {
    val bizKey: String get() = if (code.isNotEmpty()) code else name
    val key: String get() = id.ifEmpty { bizKey }
    /** Recount (if entered) overrides the first count. */
    val finalQty: Double? get() = second ?: first
}

/** An archived stocktake period (summary; the items live in history/<id>.json). */
data class Period(
    val id: Long, val label: String, val dateText: String,
    val total: Int, val counted: Int, val shortSum: Double, val surSum: Double
)

class BackupInfo(val total: Int, val counted: Int, val warehouse: String)

/** One line of the discrepancy report. */
data class Rep(val item: Item, val qty: Double, val diff: Double)

fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

fun latin(s: String): String = buildString {
    for (c in s) when (c) {
        in '۰'..'۹' -> append('0' + (c - '۰'))
        in '٠'..'٩' -> append('0' + (c - '٠'))
        else -> append(c)
    }
}

/** Parses numbers, including Persian/Arabic digits and thousand separators. */
fun num(s: String): Double? {
    val sb = StringBuilder()
    for (c in latin(s.trim())) {
        when (c) {
            ',', '٬' -> {}
            '٫' -> sb.append('.')
            else -> sb.append(c)
        }
    }
    return sb.toString().toDoubleOrNull()
}

/** Item codes: avoids "6.26E12" / "1001.0" artifacts from numeric Excel cells. */
fun normCode(raw: String): String {
    val s = latin(raw.trim())
    if (Regex("^\\d+(\\.\\d+)?[eE]\\+?\\d+$").matches(s)) {
        try { return BigDecimal(s).toBigInteger().toString() } catch (e: Exception) { }
    }
    return if (Regex("^\\d+\\.0+$").matches(s)) s.substringBefore('.') else s
}

val DEFAULT_FORMS = listOf("قرص", "کپسول", "شربت", "آمپول", "قطره", "کرم", "پماد", "ژل", "اسپری", "شیاف", "پودر", "محلول")

class Store(private val file: File) {
    val items = mutableStateListOf<Item>()
    val categories = mutableStateListOf<String>()
    val forms = mutableStateListOf<String>().also { it.addAll(DEFAULT_FORMS) }
    var epoch by mutableIntStateOf(0)
    var warehouse by mutableStateOf("انبار مرکزی")
    private val io = Executors.newSingleThreadExecutor()
    val periods = mutableStateListOf<Period>()
    private val handler = Handler(Looper.getMainLooper())
    private var pending = false
    private val saveTask = Runnable { pending = false; doSave() }
    private val histDir get() = File(file.parentFile, "history").apply { mkdirs() }
    private val indexFile get() = File(histDir, "index.json")

    /** Debounced: many keystrokes in a row (big lists) cause one disk write. */
    fun save() {
        pending = true
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, 600)
    }

    fun flush() {
        if (pending) { handler.removeCallbacks(saveTask); pending = false; doSave() }
    }

    private fun loadPeriods() {
        try {
            if (!indexFile.exists()) return
            val a = JSONArray(indexFile.readText())
            periods.clear()
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                periods.add(Period(j.getLong("id"), j.getString("l"), j.optString("d", ""), j.getInt("t"), j.getInt("c"), j.getDouble("sh"), j.getDouble("su")))
            }
        } catch (e: Exception) { }
    }

    private fun saveIndex() {
        val a = JSONArray()
        periods.forEach {
            a.put(JSONObject().put("id", it.id).put("l", it.label).put("d", it.dateText).put("t", it.total).put("c", it.counted).put("sh", it.shortSum).put("su", it.surSum))
        }
        indexFile.writeText(a.toString())
    }

    private fun itemsJson(list: List<Item>): JSONArray {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("k", it.code).put("n", it.name).put("s", it.system)
                    .put("f", it.first ?: JSONObject.NULL).put("g", it.second ?: JSONObject.NULL).put("c", it.category).put("d", it.form)
                    .put("b", JSONArray(it.barcodes)).put("i", it.key).put("m", it.manual)
            )
        }
        return arr
    }

    private fun itemFrom(j: JSONObject): Item {
        val code = j.optString("k", "")
        val name = j.getString("n")
        val bs = j.optJSONArray("b")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        return Item(
            code, name, j.getDouble("s"),
            if (j.isNull("f")) null else j.getDouble("f"),
            if (j.isNull("g")) null else j.getDouble("g"),
            j.optString("c", ""), j.optString("d", ""), bs,
            j.optString("i", "").ifEmpty { code.ifEmpty { name } }, j.optBoolean("m", false)
        )
    }

    /** Saves a snapshot of the current stocktake. reset = start a new period (clears counts). */
    fun archive(label: String, reset: Boolean): Boolean {
        val counted = items.filter { it.finalQty != null }
        if (counted.isEmpty()) return false
        var sh = 0.0; var su = 0.0
        for (i in counted) { val d = i.finalQty!! - i.system; if (d < 0) sh -= d else su += d }
        val id = System.currentTimeMillis()
        File(histDir, "$id.json").writeText(itemsJson(items.toList()).toString())
        periods.add(0, Period(id, label.trim().ifEmpty { warehouse }, Jalali.dateTime(), items.size, counted.size, sh, su))
        saveIndex()
        if (reset) resetCounts()
        return true
    }

    fun loadPeriod(id: Long): List<Item> = try {
        val a = JSONArray(File(histDir, "$id.json").readText())
        (0 until a.length()).map { itemFrom(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    fun deletePeriod(id: Long) {
        periods.removeAll { it.id == id }
        try { File(histDir, "$id.json").delete(); saveIndex() } catch (e: Exception) { }
    }

    fun load() {
        loadPeriods()
        try {
            if (!file.exists()) return
            val o = JSONObject(file.readText())
            warehouse = o.optString("w", warehouse)
            categories.clear()
            o.optJSONArray("categories")?.let { for (i in 0 until it.length()) categories.add(it.getString(i)) }
            o.optJSONArray("forms")?.let { forms.clear(); for (i in 0 until it.length()) forms.add(it.getString(i)) }
            items.clear()
            o.optJSONArray("items")?.let {
                for (i in 0 until it.length()) {
                    items.add(itemFrom(it.getJSONObject(i)))
                }
            }
        } catch (e: Exception) { /* ignore corrupted file */ }
    }

    private fun buildJson(): String {
        val arr = itemsJson(items.toList())
        return JSONObject().put("categories", JSONArray(categories.toList())).put("items", arr)
            .put("w", warehouse).put("forms", JSONArray(forms.toList())).toString()
    }

    private fun doSave() {
        val text = buildJson()
        io.execute { try { file.writeText(text) } catch (e: Exception) { } }
    }

    /** Whole current data as JSON text (for sending to another phone). */
    fun exportJson(): String = buildJson()

    fun peekBackup(text: String): BackupInfo? = try {
        val o = JSONObject(text)
        val a = o.getJSONArray("items")
        var c = 0
        for (i in 0 until a.length()) {
            val j = a.getJSONObject(i)
            if (!j.isNull("f") || !j.isNull("g")) c++
        }
        BackupInfo(a.length(), c, o.optString("w", ""))
    } catch (e: Exception) { null }

    /** merge = true: incoming counts are written onto matching items (new items are added); false: replace everything. */
    fun importBackup(text: String, merge: Boolean): Int {
        val o = JSONObject(text)
        val a = o.getJSONArray("items")
        val incoming = (0 until a.length()).map { itemFrom(a.getJSONObject(it)) }
        val cats = (0 until (o.optJSONArray("categories")?.length() ?: 0)).map { o.getJSONArray("categories").getString(it) }
        val fs = (0 until (o.optJSONArray("forms")?.length() ?: 0)).map { o.getJSONArray("forms").getString(it) }
        var n = 0
        if (!merge) {
            items.clear(); items.addAll(incoming)
            categories.clear(); categories.addAll(cats)
            if (fs.isNotEmpty()) { forms.clear(); forms.addAll(fs) }
            warehouse = o.optString("w", warehouse)
            n = incoming.size
        } else {
            val idx = items.indices.associateBy { items[it].bizKey }
            for (inc in incoming) {
                val i = idx[inc.bizKey]
                if (i == null) { items.add(inc.copy(id = uniqueId(inc.key))); n++ }
                else {
                    val cur = items[i]
                    val upd = cur.copy(
                        first = inc.first ?: cur.first, second = inc.second ?: cur.second,
                        category = cur.category.ifEmpty { inc.category }, form = cur.form.ifEmpty { inc.form },
                        barcodes = (cur.barcodes + inc.barcodes).distinct()
                    )
                    if (upd != cur) { items[i] = upd; n++ }
                }
            }
            cats.filter { it.isNotEmpty() && it !in categories }.forEach { categories.add(it) }
            fs.filter { it.isNotEmpty() && it !in forms }.forEach { forms.add(it) }
        }
        epoch++
        save()
        return n
    }

    private fun upd(key: String, f: (Item) -> Item) {
        val i = items.indexOfFirst { it.key == key }
        if (i >= 0) { items[i] = f(items[i]); save() }
    }

    fun setCount(key: String, stage: Int, v: Double?) = upd(key) { if (stage == 1) it.copy(first = v) else it.copy(second = v) }
    fun setCategory(key: String, c: String) = upd(key) { it.copy(category = c) }

    /** Finds an item by a scanned barcode: any of its barcodes, then the drug code, then the name; digits-only values ignore leading zeros (UPC-A vs EAN-13). */
    fun findByCode(raw: String): Item? {
        val c = latin(raw.trim())
        if (c.isEmpty()) return null
        items.firstOrNull { it.barcodes.any { b -> b.equals(c, ignoreCase = true) } }?.let { return it }
        items.firstOrNull { it.code.isNotEmpty() && it.code.equals(c, ignoreCase = true) }?.let { return it }
        items.firstOrNull { it.name == c }?.let { return it }
        val z = c.trimStart('0')
        if (z.isNotEmpty() && c.all { it.isDigit() }) {
            items.firstOrNull { it.barcodes.any { b -> b.all { ch -> ch.isDigit() } && b.trimStart('0') == z } }?.let { return it }
            items.firstOrNull { it.code.isNotEmpty() && it.code.all { ch -> ch.isDigit() } && it.code.trimStart('0') == z }?.let { return it }
        }
        return null
    }

    private fun uniqueId(base: String): String {
        var id = base
        var n = 2
        while (items.any { it.key == id }) { id = "$base#$n"; n++ }
        return id
    }

    /** Returns an error message if the item clashes with another one (same code or same barcode), else null. */
    fun checkItem(item: Item, ignoreKey: String?): String? {
        if (item.name.isBlank()) return "نام کالا را وارد کنید"
        val others = items.filter { o -> o.key != ignoreKey }
        if (item.code.isNotEmpty()) {
            others.firstOrNull { o -> o.code == item.code }?.let { o -> return "کد «${item.code}» قبلاً برای «${o.name}» ثبت شده است" }
        } else if (others.any { o -> o.code.isEmpty() && o.name == item.name }) {
            return "کالایی با همین نام (و بدون کد) وجود دارد"
        }
        for (b in item.barcodes) {
            others.firstOrNull { o -> b in o.barcodes }?.let { o -> return "بارکد $b قبلاً برای «${o.name}» ثبت شده است" }
        }
        return null
    }

    /** Adds an item by hand. Returns an error message or null on success. */
    fun addItem(item: Item): String? {
        val clean = item.copy(name = item.name.trim(), code = item.code.trim(), barcodes = item.barcodes.distinct())
        checkItem(clean, null)?.let { return it }
        items.add(0, clean.copy(id = uniqueId(clean.bizKey), manual = true))
        epoch++
        save()
        return null
    }

    fun updateItem(key: String, item: Item): String? {
        val i = items.indexOfFirst { it.key == key }
        if (i < 0) return "کالا پیدا نشد"
        val clean = item.copy(name = item.name.trim(), code = item.code.trim(), barcodes = item.barcodes.distinct(), id = key)
        checkItem(clean, key)?.let { return it }
        items[i] = clean.copy(manual = items[i].manual)
        save()
        return null
    }

    fun deleteItem(key: String) {
        items.removeAll { it.key == key }
        epoch++
        save()
    }

    fun addCategory(c: String) {
        val t = c.trim()
        if (t.isNotEmpty() && t !in categories) { categories.add(t); save() }
    }

    fun renameCategory(old: String, newName: String): Boolean {
        val t = newName.trim()
        val i = categories.indexOf(old)
        if (i < 0 || t.isEmpty() || (t != old && t in categories)) return false
        categories[i] = t
        for (j in items.indices) if (items[j].category == old) items[j] = items[j].copy(category = t)
        save()
        return true
    }

    fun setForm(key: String, f: String) = upd(key) { it.copy(form = f) }

    fun addForm(f: String) {
        val t = f.trim()
        if (t.isNotEmpty() && t !in forms) { forms.add(t); save() }
    }

    fun renameForm(old: String, newName: String): Boolean {
        val t = newName.trim()
        val i = forms.indexOf(old)
        if (i < 0 || t.isEmpty() || (t != old && t in forms)) return false
        forms[i] = t
        for (j in items.indices) if (items[j].form == old) items[j] = items[j].copy(form = t)
        save()
        return true
    }

    fun deleteForm(f: String) {
        forms.remove(f)
        for (i in items.indices) if (items[i].form == f) items[i] = items[i].copy(form = "")
        save()
    }

    fun deleteCategory(c: String) {
        categories.remove(c)
        for (i in items.indices) if (items[i].category == c) items[i] = items[i].copy(category = "")
        save()
    }

    fun resetCounts() {
        for (i in items.indices) items[i] = items[i].copy(first = null, second = null)
        epoch++
        save()
    }

    /** Header names are auto-detected. Without a header: A = code, B = name, C = system stock (or B/C = name/stock for 2 columns). */
    fun importRows(rows: List<List<String>>): Int {
        if (rows.isEmpty()) return 0
        val h = rows[0].map { it.trim().lowercase() }
        val isBar = { c: String -> c.contains("بارکد") || c.contains("barcode") || c.contains("gtin") || c.contains("ean") }
        var bi = h.indexOfFirst(isBar)
        var fi = h.indexOfFirst { it.contains("شکل") || it.contains("form") || it.contains("dosage") }
        var ni = h.indexOfFirst { it.contains("نام") || it.contains("name") }
        if (ni < 0) ni = h.indices.firstOrNull { it != fi && it != bi && (h[it].contains("کالا") || h[it].contains("دارو")) } ?: -1
        var si = h.indexOfFirst { it.contains("موجودی") || it.contains("stock") || it.contains("qty") || it.contains("تعداد") }
        var ci = h.indexOfFirst { it.contains("دسته") || it.contains("category") || it.contains("group") }
        var ki = h.indexOfFirst { !isBar(it) && (it.contains("کد") || it.contains("code")) }
        val hasHeader = ni >= 0 && si >= 0 && ni != si
        if (!hasHeader) {
            ci = -1; fi = -1; bi = -1
            if (rows[0].size >= 3) { ki = 0; ni = 1; si = 2 } else { ki = -1; ni = 0; si = 1 }
        }
        val data = if (hasHeader) rows.drop(1) else rows
        val old = items.associateBy { it.bizKey }
        val map = LinkedHashMap<String, Item>()
        for (r in data) {
            val name = r.getOrNull(ni)?.trim().orEmpty()
            if (name.isEmpty()) continue
            val code = if (ki >= 0) normCode(r.getOrNull(ki).orEmpty()) else ""
            val bars = if (bi >= 0) splitBarcodes(r.getOrNull(bi).orEmpty()) else emptyList()
            val s = num(r.getOrNull(si).orEmpty()) ?: 0.0
            val fc = if (ci >= 0) r.getOrNull(ci)?.trim().orEmpty() else ""
            val ff = if (fi >= 0) r.getOrNull(fi)?.trim().orEmpty() else ""
            val key = if (code.isNotEmpty()) code else name
            val e = map[key]
            map[key] = if (e != null) {
                e.copy(
                    system = e.system + s, barcodes = (e.barcodes + bars).distinct(),
                    category = e.category.ifEmpty { fc }, form = e.form.ifEmpty { ff }
                )
            } else {
                val o = old[key]
                Item(
                    code = code, name = name, system = s, first = o?.first, second = o?.second,
                    category = if (fc.isNotEmpty()) fc else o?.category ?: "",
                    form = if (ff.isNotEmpty()) ff else o?.form ?: "",
                    barcodes = (bars + (o?.barcodes ?: emptyList())).distinct(),
                    id = o?.key ?: key, manual = false
                )
            }
        }
        // items added by hand and not present in the new file are kept
        for (o in items) if (o.manual && o.bizKey !in map) map[o.bizKey] = o
        map.values.map { it.category }.filter { it.isNotEmpty() && it !in categories }.distinct().forEach { categories.add(it) }
        map.values.map { it.form }.filter { it.isNotEmpty() && it !in forms }.distinct().forEach { forms.add(it) }
        items.clear()
        items.addAll(map.values)
        epoch++
        save()
        return map.size
    }
}

/** A cell may hold several barcodes separated by spaces, new lines, commas or semicolons. */
fun splitBarcodes(raw: String): List<String> =
    raw.split(Regex("[\\s,،;؛/|]+")).map { normCode(it) }.filter { it.isNotEmpty() }.distinct()
