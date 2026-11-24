package com.hesabgara.anbargardan

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class Editing(val key: String?)

/** Add a new item by hand (key = null) or edit/delete an existing one. Drug code and barcodes are separate; an item can have many barcodes. */
@Composable
fun ItemEditorSheet(store: Store, key: String?, onScan: ((String) -> Unit) -> Unit, onClose: () -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    val existing = remember(key) { key?.let { k -> store.items.firstOrNull { it.key == k } } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var code by remember { mutableStateOf(existing?.code ?: "") }
    var sys by remember { mutableStateOf(existing?.system?.let { fmt(it) } ?: "") }
    var cat by remember { mutableStateOf(existing?.category ?: "") }
    var form by remember { mutableStateOf(existing?.form ?: "") }
    val barcodes = remember { mutableStateListOf<String>().also { l -> existing?.barcodes?.let { l.addAll(it) } } }
    var newBar by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun addBar(raw: String) {
        for (b in splitBarcodes(raw)) if (b !in barcodes) barcodes.add(b)
        newBar = ""
    }

    Sheet(onClose, if (existing == null) "افزودن کالا" else "ویرایش کالا") {
        AppField(name, { name = it }, label = "نام کالا *", hint = "مثلاً آموکسی‌سیلین ۵۰۰")
        AppField(code, { code = it }, label = "کد کالا / کد دارو", hint = "اختیاری؛ جدا از بارکد")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("بارکدها", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.mut, modifier = Modifier.padding(start = 2.dp))
            barcodes.toList().forEach { b ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.s2).padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\u200E$b", Modifier.weight(1f), fontSize = 14.sp, color = t.ink)
                    IconBtn(Icons.Outlined.Close, { barcodes.remove(b) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppField(newBar, { newBar = it }, hint = "بارکد را بنویسید", modifier = Modifier.weight(1f))
                IconBtn(Icons.Outlined.QrCodeScanner, { onScan { raw -> addBar(raw) } }, tint = t.pri)
                IconBtn(Icons.Outlined.Add, { addBar(newBar) }, tint = t.pri)
            }
            Text("هر کالا می‌تواند چند بارکد داشته باشد؛ با اسکن هر کدام، همین کالا باز می‌شود.", fontSize = 11.5.sp, color = t.mut)
        }
        AppField(sys, { sys = it }, label = "موجودی سیستم", hint = "۰", keyboardType = KeyboardType.Decimal)
        SelectField("دسته", cat.ifEmpty { "بدون دسته" }, store.categories.toList(), "بدون دسته") { i -> cat = if (i < 0) "" else store.categories[i] }
        SelectField("شکل دارویی", form.ifEmpty { "بدون شکل" }, store.forms.toList(), "بدون شکل") { i -> form = if (i < 0) "" else store.forms[i] }
        error?.let { StatusBanner(Tone.Bad, Icons.Outlined.Warning, it) }
        PrimaryButton("ذخیره", {
            if (newBar.isNotBlank()) addBar(newBar)
            val item = Item(
                code = code.trim(), name = name.trim(), system = num(sys) ?: 0.0, first = existing?.first, second = existing?.second,
                category = cat, form = form, barcodes = barcodes.toList()
            )
            val e = if (existing == null) store.addItem(item) else store.updateItem(existing.key, item)
            if (e != null) error = e
            else {
                Toast.makeText(ctx, "ذخیره شد", Toast.LENGTH_SHORT).show()
                onClose()
            }
        }, icon = Icons.Outlined.Check)
        if (existing != null) DangerButton("حذف کالا", { confirmDelete = true }, icon = Icons.Outlined.Delete)
    }
    if (confirmDelete && existing != null) {
        AppDialog({ confirmDelete = false }, "«${existing.name}» حذف شود؟") {
            Text("کالا و شمارش‌های آن از این دوره پاک می‌شود.", color = t.mut)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DangerButton("حذف", { store.deleteItem(existing.key); confirmDelete = false; onClose(); onDeleted() }, Modifier.weight(1f))
                SecondaryButton("انصراف", { confirmDelete = false }, Modifier.weight(1f))
            }
        }
    }
}
