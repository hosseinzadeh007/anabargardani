package com.hesabgara.anbargardan

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Minimal dependency-free .xlsx reader/writer. */
object Excel {
    private fun parser(b: ByteArray): XmlPullParser =
        Xml.newPullParser().also { it.setInput(ByteArrayInputStream(b), "UTF-8") }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch.isLetter()) n = n * 26 + (ch.uppercaseChar() - 'A' + 1) else break }
        return n - 1
    }

    fun read(input: InputStream): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "xl/sharedStrings.xml" || e.name.startsWith("xl/worksheets/sheet")) files[e.name] = z.readBytes()
                e = z.nextEntry
            }
        }
        val key = files.keys.filter { it.startsWith("xl/worksheets/sheet") }.minOrNull()
            ?: throw IllegalArgumentException("فایل اکسل معتبر نیست (فقط xlsx)")

        val ss = ArrayList<String>()
        files["xl/sharedStrings.xml"]?.let { b ->
            val p = parser(b); val sb = StringBuilder(); var inT = false
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> { if (p.name == "si") sb.setLength(0); if (p.name == "t") inT = true }
                    XmlPullParser.END_TAG -> { if (p.name == "t") inT = false; if (p.name == "si") ss.add(sb.toString()) }
                    XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                }
                ev = p.next()
            }
        }

        val rows = ArrayList<List<String>>()
        val p = parser(files[key]!!)
        var row = ArrayList<String>(); var col = 0; var type = ""
        var inV = false; var inT = false; val sb = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = ArrayList()
                    "c" -> {
                        type = p.getAttributeValue(null, "t") ?: ""
                        col = colIndex(p.getAttributeValue(null, "r") ?: "")
                        sb.setLength(0)
                    }
                    "v" -> inV = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inV || inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val raw = sb.toString()
                        val value = if (type == "s") ss.getOrElse(raw.trim().toIntOrNull() ?: -1) { "" } else raw
                        if (col >= 0) { while (row.size <= col) row.add(""); row[col] = value }
                    }
                    "row" -> rows.add(row)
                }
            }
            ev = p.next()
        }
        return rows
    }

    fun write(out: OutputStream, rows: List<List<Any?>>) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews><sheetData>""")
        rows.forEachIndexed { r, row ->
            sb.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, v ->
                val ref = "${'A' + c}${r + 1}"
                when (v) {
                    null -> {}
                    is Number -> sb.append("<c r=\"$ref\"><v>$v</v></c>")
                    else -> sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${esc(v.toString())}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        ZipOutputStream(out).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$rel/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$rel"><sheets><sheet name="Report" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$rel/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            put("xl/worksheets/sheet1.xml", sb.toString())
        }
    }
}
