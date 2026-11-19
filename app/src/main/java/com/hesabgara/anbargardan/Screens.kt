package com.hesabgara.anbargardan

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ------------------------------------------------------------------ import */
@Composable
fun ImportScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val rows = ctx.contentResolver.openInputStream(uri)!!.use { Excel.read(it) }
                val n = store.importRows(rows)
                Toast.makeText(ctx, "${faI(n)} کالا وارد شد", Toast.LENGTH_SHORT).show()
                onBack()
            } catch (e: Exception) {
                Toast.makeText(ctx, "خطا در خواندن فایل: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        AppBar("ورود از اکسل", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBubble(Icons.Outlined.Description, Tone.Pri, 72.dp)
                Spacer(Modifier.height(12.dp))
                Text("فایل اکسل کالاها را انتخاب کنید", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.ink)
            }
            PrimaryButton("انتخاب فایل", { picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) }, icon = Icons.Outlined.FileUpload)
            SectionTitle("راهنمای ستون‌ها")
            val guide = listOf(
                "الزامی" to "نام کالا · موجودی سیستم", "اختیاری" to "کد کالا (بارکد) · شکل دارویی · دسته",
                "فرمت" to "Excel 2007 به بعد (.xlsx)", "نکته" to "شمارش‌های قبلیِ کالاهای مشترک حفظ می‌شود"
            )
            Panel {
                guide.forEachIndexed { i, (a, b) ->
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(a, Modifier.width(64.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.pri)
                        Text(b, fontSize = 13.5.sp, color = t.ink)
                    }
                    if (i < guide.lastIndex) HorizontalDivider(color = t.line)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ entry */
@Composable
fun EntryScreen(store: Store, key: String, scanned: Boolean, onBack: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val t = LocalTk.current
    val item = store.items.firstOrNull { it.key == key }
    if (item == null) { LaunchedEffect(Unit) { onBack() }; return }
    var recount by remember(key) { mutableStateOf(item.second != null) }
    var text by remember(key) { mutableStateOf((if (item.second != null) item.second else item.first)?.let { fmt(it) } ?: "") }
    var details by remember { mutableStateOf(false) }
    val v = num(text)
    val diff = v?.minus(item.system)
    val fr = remember { FocusRequester() }
    LaunchedEffect(key, recount) { try { fr.requestFocus() } catch (e: Exception) { } }
    fun setMode(r: Boolean) { recount = r; text = (if (r) item.second else item.first)?.let { fmt(it) } ?: "" }
    fun bump(d: Double) { text = fmt(((num(text) ?: 0.0) + d).coerceAtLeast(0.0)) }

    Column(Modifier.fillMaxSize()) {
        AppBar("ثبت موجودی", onBack = onBack, actions = { IconBtn(Icons.Outlined.Sell, { details = true }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(formIcon(item.form), Tone.Pri, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = t.ink)
                        Text(
                            listOfNotNull(item.code.takeIf { it.isNotEmpty() }?.let { fa(it) }, item.form.takeIf { it.isNotEmpty() }, item.category.takeIf { it.isNotEmpty() }).joinToString(" · "),
                            fontSize = 12.sp, color = t.mut
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("موجودی سیستم", fontSize = 11.sp, color = t.mut)
                        Text(faN(item.system), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
                    }
                }
            }
            Segmented(listOf("شمارش اول", "شمارش مجدد"), if (recount) 1 else 0) { setMode(it == 1) }
            if (recount) Text("شمارش اول: ${item.first?.let { faN(it) } ?: "ثبت نشده"}", fontSize = 12.5.sp, color = t.mut)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundStep(Icons.Outlined.Add) { bump(1.0) }
                val shape = RoundedCornerShape(18.dp)
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = t.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(t.pri),
                    modifier = Modifier.weight(1f).height(72.dp).focusRequester(fr),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize().clip(shape).background(t.s).border(1.5.dp, t.pri, shape), contentAlignment = Alignment.Center) {
                            if (text.isEmpty()) Text("۰", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = t.mut.copy(alpha = 0.5f))
                            inner()
                        }
                    }
                )
                RoundStep(Icons.Outlined.Remove) { bump(-1.0) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-10.0, -5.0, -1.0, 1.0, 5.0, 10.0).forEach { st ->
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(t.s2).clickable { bump(st) },
                        contentAlignment = Alignment.Center
                    ) { Text(if (st > 0) "+${faN(st)}" else "−${faN(-st)}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = t.ink) }
                }
            }
            when {
                diff == null -> StatusBanner(Tone.Neutral, Icons.Outlined.Info, "تعداد را وارد کنید")
                diff < -1e-9 -> StatusBanner(Tone.Bad, Icons.Outlined.ArrowDownward, "اختلاف ${signed(diff)} (کسری)")
                diff > 1e-9 -> StatusBanner(Tone.Ok, Icons.Outlined.ArrowUpward, "اختلاف ${signed(diff)} (اضافه)")
                else -> StatusBanner(Tone.Pri, Icons.Outlined.CheckCircle, "مطابق با سیستم")
            }
            PrimaryButton(if (scanned) "ثبت و اسکن بعدی" else "ثبت", {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                store.setCount(key, if (recount) 2 else 1, v)
                onSaved()
            }, icon = Icons.Outlined.Check)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (details) {
        val live = store.items.firstOrNull { it.key == key } ?: item
        Sheet({ details = false }, "دسته و شکل دارویی") {
            SelectField("دسته", live.category.ifEmpty { "بدون دسته" }, store.categories.toList(), "بدون دسته") { i ->
                store.setCategory(key, if (i < 0) "" else store.categories[i])
            }
            SelectField("شکل دارویی", live.form.ifEmpty { "بدون شکل" }, store.forms.toList(), "بدون شکل") { i ->
                store.setForm(key, if (i < 0) "" else store.forms[i])
            }
            PrimaryButton("تأیید", { details = false })
        }
    }
}

@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val t = LocalTk.current
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.size(56.dp).clip(shape).background(t.s).border(1.5.dp, t.line, shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = t.pri)
    }
}

/* ------------------------------------------------------------------ report */
@Composable
private fun TRow(a: String, b: String, c: String, d: String, header: Boolean = false, dColor: Color = Color.Unspecified) {
    val t = LocalTk.current
    Row(
        Modifier.fillMaxWidth().then(if (header) Modifier.background(t.s2) else Modifier).padding(horizontal = 14.dp, vertical = if (header) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fs = if (header) 11.5.sp else 13.sp
        val w = if (header) FontWeight.Bold else FontWeight.Normal
        val col = if (header) t.mut else t.ink
        Text(a, Modifier.weight(2.3f), fontSize = fs, fontWeight = if (header) w else FontWeight.Medium, color = col)
        Text(b, Modifier.weight(1f), fontSize = fs, fontWeight = w, color = col, textAlign = TextAlign.Center)
        Text(c, Modifier.weight(1f), fontSize = fs, fontWeight = w, color = col, textAlign = TextAlign.Center)
        Text(d, Modifier.weight(1f), fontSize = fs, fontWeight = FontWeight.ExtraBold, color = if (header) col else dColor, textAlign = TextAlign.Center)
    }
}

@Composable
private fun KpiCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: Tone, label: String, value: String) {
    val t = LocalTk.current
    val (fg, _) = toneColors(tone)
    Panel(modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, tone)
            Spacer(Modifier.width(10.dp))
            Column { Text(label, fontSize = 12.sp, color = t.mut); Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = fg) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportTable(rep: ReportData) {
    if (rep.rows.isEmpty()) {
        item { EmptyState(Icons.Outlined.BarChart, "موردی برای نمایش نیست") }
        return
    }
    item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))) { TRow("نام کالا", "سیستم", "فیزیکی", "اختلاف", header = true) } }
    items(rep.rows, key = { it.item.key }) { r ->
        val t = LocalTk.current
        Column(Modifier.fillMaxWidth().background(t.s)) {
            HorizontalDivider(color = t.line)
            TRow(
                r.item.name + (if (r.item.form.isNotEmpty()) " (${r.item.form})" else ""), faN(r.item.system), faN(r.qty), signed(r.diff),
                dColor = toneColors(if (abs2(r.diff) < 1e-9) Tone.Neutral else diffTone(r.diff)).first
            )
        }
    }
    item { Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).background(LocalTk.current.s)) }
}

@Composable
fun ReportScreen(
    store: Store, rep: ReportData, cat: String?, onCat: (String?) -> Unit, form: String?, onForm: (String?) -> Unit,
    mode: Int, onMode: (Int) -> Unit, zero: Boolean, onZero: (Boolean) -> Unit, onExport: () -> Unit
) {
    val t = LocalTk.current
    var filter by remember { mutableStateOf(false) }
    val n = (if (cat != null) 1 else 0) + (if (form != null) 1 else 0)
    Column(Modifier.fillMaxSize()) {
        AppBar("گزارش مغایرت‌ها", actions = {
            IconBtn(Icons.Outlined.FilterList, { filter = true }, badge = n)
            IconBtn(Icons.Outlined.Share, onExport)
        })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item {
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(Modifier.weight(1f), Icons.Outlined.ArrowDownward, Tone.Bad, "جمع کسری", faN(rep.shortSum))
                    KpiCard(Modifier.weight(1f), Icons.Outlined.ArrowUpward, Tone.Ok, "جمع اضافه", faN(rep.surSum))
                }
            }
            item { Box(Modifier.padding(bottom = 6.dp)) { Segmented(listOf("همه", "کسری", "اضافه"), mode, onMode) } }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("شمارش‌نشده‌ها صفر حساب شوند", Modifier.weight(1f), fontSize = 12.sp, color = t.mut)
                    Switch(checked = zero, onCheckedChange = onZero, colors = SwitchDefaults.colors(checkedTrackColor = t.pri))
                }
            }
            reportTable(rep)
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    if (filter) {
        Sheet({ filter = false }, "فیلتر گزارش") {
            SelectField("دسته", cat ?: "همه دسته‌ها", store.categories.toList(), "همه دسته‌ها") { i -> onCat(if (i < 0) null else store.categories[i]) }
            SelectField("شکل دارویی", form ?: "همه شکل‌ها", store.forms.toList(), "همه شکل‌ها") { i -> onForm(if (i < 0) null else store.forms[i]) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("پاک‌کردن", { onCat(null); onForm(null) }, Modifier.weight(1f))
                PrimaryButton("اعمال", { filter = false }, Modifier.weight(1f))
            }
        }
    }
}

private fun abs2(d: Double) = if (d < 0) -d else d

/* ------------------------------------------------------------------ export */
@Composable
fun ExportScreen(rows: List<Rep>, scope: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    var xl by remember { mutableStateOf(true) }
    var pd by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        AppBar("خروجی و چاپ", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${faI(rows.size)} ردیف در گزارش فعلی · $scope", fontSize = 12.5.sp, color = t.mut)
            Panel {
                ListRow(Icons.Outlined.Description, "خروجی Excel", "فایل xlsx با ستون‌های کامل", Tone.Pri, onClick = { xl = !xl }, chevron = false, trailing = { CheckMark(xl) })
                ListRow(Icons.Outlined.PictureAsPdf, "خروجی PDF", "گزارش فارسی آماده‌ی چاپ", Tone.Bad, onClick = { pd = !pd }, chevron = false, trailing = { CheckMark(pd) })
                ListRow(Icons.Outlined.Print, "چاپ مستقیم", "ارسال به چاپگر بلوتوث یا شبکه", Tone.Ok, divider = false, onClick = {
                    if (rows.isEmpty()) Toast.makeText(ctx, "گزارشی برای چاپ وجود ندارد", Toast.LENGTH_SHORT).show() else Export.print(ctx, rows, scope)
                })
            }
            PrimaryButton("اشتراک‌گذاری گزارش", { Export.shareFiles(ctx, rows, scope, xl, pd) }, icon = Icons.Outlined.Share, enabled = rows.isNotEmpty() && (xl || pd))
        }
    }
}

/* ------------------------------------------------------------------ progress by category */
@Composable
fun ProgressScreen(store: Store, sel: Set<String>, onSel: (Set<String>) -> Unit, onBack: () -> Unit) {
    val t = LocalTk.current
    val groups = (store.categories.toList().map { c -> c to store.items.filter { it.category == c } } +
        listOf("" to store.items.filter { it.category.isEmpty() })).filter { it.second.isNotEmpty() }
    val scope = store.items.filter { it.category in sel }
    val done = scope.count { it.finalQty != null }
    val p = if (scope.isEmpty()) 0f else done.toFloat() / scope.size
    Column(Modifier.fillMaxSize()) {
        AppBar("پیشرفت انبارگردانی", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel {
                Column(Modifier.padding(16.dp)) {
                    Text(if (sel.isEmpty()) "هیچ دسته‌ای انتخاب نشده" else "${faI(sel.size)} دسته انتخاب شده", fontSize = 12.5.sp, color = t.mut)
                    Text("${faI((p * 100).toInt())}٪", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
                    Spacer(Modifier.height(8.dp))
                    Track(p)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (sel.isEmpty()) "با انتخاب دسته‌ها پیشرفت همان بخش نمایش داده می‌شود"
                        else "${faI(done)} از ${faI(scope.size)} کالا شمارش شده · ${faI(scope.size - done)} باقی مانده",
                        fontSize = 12.5.sp, color = t.mut
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GhostButton("انتخاب همه", { onSel(groups.map { it.first }.toSet()) })
                GhostButton("هیچ‌کدام", { onSel(emptySet()) })
            }
            Panel {
                groups.forEachIndexed { i, (key, list) ->
                    val on = key in sel
                    val d = list.count { it.finalQty != null }
                    val gp = d.toFloat() / list.size
                    Column(Modifier.fillMaxWidth().clickable { onSel(if (on) sel - key else sel + key) }.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CheckMark(on)
                            Spacer(Modifier.width(12.dp))
                            Text(key.ifEmpty { "بدون دسته" }, Modifier.weight(1f), fontWeight = FontWeight.Medium, color = t.ink)
                            Text("${faI(d)} از ${faI(list.size)} · ${faI((gp * 100).toInt())}٪", fontSize = 12.sp, color = t.mut)
                        }
                        Spacer(Modifier.height(10.dp))
                        Track(gp, color = if (on) t.ok else t.pri)
                    }
                    if (i < groups.lastIndex) HorizontalDivider(color = t.line)
                }
            }
            PrimaryButton("تأیید", onBack)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ------------------------------------------------------------------ history */
@Composable
fun HistoryScreen(store: Store, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val t = LocalTk.current
    var del by remember { mutableStateOf<Period?>(null) }
    Column(Modifier.fillMaxSize()) {
        AppBar("دوره‌های قبل", onBack = onBack)
        if (store.periods.isEmpty()) EmptyState(Icons.Outlined.Archive, "هنوز دوره‌ای بایگانی نشده است.\nاز «تنظیمات» گزینه‌ی «بایگانی دوره فعال» را بزنید.")
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(store.periods.toList(), key = { it.id }) { p ->
                Panel(onClick = { onOpen(p.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Outlined.Archive, Tone.Pri)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.label, fontWeight = FontWeight.Bold, color = t.ink)
                            Text(p.dateText, fontSize = 12.sp, color = t.mut)
                            Text("${faI(p.counted)} از ${faI(p.total)} · کسری ${faN(p.shortSum)} · اضافه ${faN(p.surSum)}", fontSize = 12.sp, color = t.mut)
                        }
                        IconBtn(Icons.Outlined.Delete, { del = p }, tint = t.bad)
                    }
                }
            }
        }
    }
    del?.let { p ->
        AppDialog({ del = null }, "«${p.label}» حذف شود؟") {
            Text("این دوره‌ی بایگانی‌شده برای همیشه پاک می‌شود.", color = t.mut)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DangerButton("حذف", { store.deletePeriod(p.id); del = null }, Modifier.weight(1f))
                SecondaryButton("انصراف", { del = null }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PeriodScreen(store: Store, p: Period, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    val snap = remember(p.id) { store.loadPeriod(p.id) }
    var cat by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableIntStateOf(0) }
    val rep = remember(snap, cat, form, mode) { buildReportFrom(snap, cat, form, false, mode) }
    val cats = remember(snap) { snap.map { it.category }.filter { it.isNotEmpty() }.distinct().sorted() }
    val forms = remember(snap) { snap.map { it.form }.filter { it.isNotEmpty() }.distinct().sorted() }
    val scope = p.label + "  |  " + scopeText(cat, form)
    Column(Modifier.fillMaxSize()) {
        AppBar(p.label, subtitle = "بایگانی: ${p.dateText}", onBack = onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item {
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { SelectField("دسته", cat ?: "همه", cats, "همه") { i -> cat = if (i < 0) null else cats[i] } }
                    Box(Modifier.weight(1f)) { SelectField("شکل", form ?: "همه", forms, "همه") { i -> form = if (i < 0) null else forms[i] } }
                }
            }
            item { Box(Modifier.padding(bottom = 10.dp)) { Segmented(listOf("همه", "کسری", "اضافه"), mode) { mode = it } } }
            item {
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(Modifier.weight(1f), Icons.Outlined.ArrowDownward, Tone.Bad, "جمع کسری", faN(rep.shortSum))
                    KpiCard(Modifier.weight(1f), Icons.Outlined.ArrowUpward, Tone.Ok, "جمع اضافه", faN(rep.surSum))
                }
            }
            reportTable(rep)
            item {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Excel", { Export.shareFiles(ctx, rep.rows, scope, true, false) }, Modifier.weight(1f), Icons.Outlined.Description, rep.rows.isNotEmpty())
                    SecondaryButton("PDF", { Export.shareFiles(ctx, rep.rows, scope, false, true) }, Modifier.weight(1f), Icons.Outlined.PictureAsPdf, rep.rows.isNotEmpty())
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
