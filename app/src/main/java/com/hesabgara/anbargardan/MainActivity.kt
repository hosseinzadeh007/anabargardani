package com.hesabgara.anbargardan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var scanCb: ((String) -> Unit)? = null
    private lateinit var store: Store

    override fun onStop() {
        super.onStop()
        store.flush()
    }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val c = if (r.resultCode == android.app.Activity.RESULT_OK) r.data?.getStringExtra("SCAN_RESULT") else null
        if (!c.isNullOrBlank()) scanCb?.invoke(c)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(File(filesDir, "data.json")).also { it.load() }
        setContent {
            AnbarTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App(store) { cb ->
                        scanCb = cb
                        scanLauncher.launch(
                            IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
                                .setPrompt("بارکد کالا را مقابل دوربین قرار دهید")
                                .setBeepEnabled(true).setOrientationLocked(false)
                                .setCaptureActivity(PortraitCaptureActivity::class.java).createScanIntent()
                        )
                    }
                }
            }
        }
    }
}

enum class Tab { Home, Items, Report, Settings }

sealed interface Sub {
    object Import : Sub
    object Export : Sub
    object Progress : Sub
    object History : Sub
    object Send : Sub
    object Receive : Sub
    data class PeriodView(val id: Long) : Sub
    data class Entry(val key: String, val scanned: Boolean = false) : Sub
}

@Composable
fun App(store: Store, onScan: ((String) -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    var tab by remember { mutableStateOf(Tab.Home) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    var cat by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<String?>(null) }
    var pcats by remember { mutableStateOf<Set<String>>(emptySet()) }
    var q by remember { mutableStateOf("") }
    var status by remember { mutableIntStateOf(0) }
    var sort by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(0) }
    var zero by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Editing?>(null) }
    BackHandler(sub != null) { sub = if (sub is Sub.PeriodView) Sub.History else null }
    BackHandler(sub == null && tab != Tab.Home) { tab = Tab.Home }
    val rep = buildReport(store, cat, form, zero, mode)
    val main = remember { Handler(Looper.getMainLooper()) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Thread { val r = Updater.check(ctx); r.first?.let { i -> main.post { update = i } } }.start()
    }
    val checkUpdate: () -> Unit = {
        Toast.makeText(ctx, "در حال بررسی…", Toast.LENGTH_SHORT).show()
        Thread {
            val r = Updater.check(ctx)
            main.post {
                val i = r.first
                if (i != null) { update = i; showUpdate = true }
                else Toast.makeText(ctx, r.second ?: "نسخه‌ی شما به‌روز است", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    fun scanAndOpen() = onScan { raw ->
        val hit = store.findByCode(raw)
        if (hit != null) sub = Sub.Entry(hit.key, true)
        else {
            Toast.makeText(ctx, "کالایی با این کد پیدا نشد؛ کد در جستجو قرار گرفت", Toast.LENGTH_LONG).show()
            q = latin(raw.trim()); sub = null; tab = Tab.Items
        }
    }
    val goAction: (String) -> Unit = { a ->
        when (a) {
            "sh" -> { mode = 1; tab = Tab.Report }
            "su" -> { mode = 2; tab = Tab.Report }
            "rc" -> { status = 4; tab = Tab.Items }
            else -> { status = 1; tab = Tab.Items }
        }
    }

    Surface(Modifier.fillMaxSize(), color = t.bg) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                Crossfade(targetState = sub to tab, animationSpec = tween(160), label = "nav") { (s, tb) ->
                    when (s) {
                        is Sub.Import -> ImportScreen(store) { sub = null }
                        is Sub.Export -> ExportScreen(rep.rows, scopeText(cat, form)) { sub = null }
                        is Sub.Progress -> ProgressScreen(store, pcats, { pcats = it }) { sub = null }
                        is Sub.History -> HistoryScreen(store, { sub = Sub.PeriodView(it) }) { sub = null }
                        is Sub.PeriodView -> store.periods.firstOrNull { it.id == s.id }?.let { p -> PeriodScreen(store, p) { sub = Sub.History } }
                        is Sub.Send -> SendScreen(store, onScan) { sub = null }
                        is Sub.Receive -> ReceiveScreen(store) { sub = null }
                        is Sub.Entry -> EntryScreen(store, s.key, s.scanned, { sub = null }, { sub = null; if (s.scanned) scanAndOpen() }) { editor = Editing(s.key) }
                        null -> when (tb) {
                            Tab.Home -> HomeScreen(
                                store, pcats, { sub = Sub.Progress }, { sub = Sub.Import }, { sub = Sub.Export },
                                { sub = Sub.Send }, { sub = Sub.History }, goAction, update, { showUpdate = true }, { editor = Editing(null) }
                            )
                            Tab.Items -> ItemsScreen(
                                store, q, { q = it }, status, { status = it }, sort, { sort = it },
                                cat, { cat = it }, form, { form = it }, { scanAndOpen() }, { editor = Editing(null) }
                            ) { sub = Sub.Entry(it) }
                            Tab.Report -> ReportScreen(store, rep, cat, { cat = it }, form, { form = it }, mode, { mode = it }, zero, { zero = it }) { sub = Sub.Export }
                            Tab.Settings -> SettingsScreen(store, { sub = Sub.History }, { sub = Sub.Send }, { sub = Sub.Receive }, { sub = Sub.Import }, checkUpdate, { editor = Editing(null) })
                        }
                    }
                }
                if (sub == null && (tab == Tab.Home || tab == Tab.Items)) {
                    ExtendedFloatingActionButton(
                        onClick = { scanAndOpen() }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                        containerColor = t.acc, contentColor = Color(0xFF1B1200)
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text("اسکن بارکد", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            update?.let { u -> if (showUpdate) UpdateDialog(u) { showUpdate = false } }
            editor?.let { e -> ItemEditorSheet(store, e.key, onScan, { editor = null }) { if (sub is Sub.Entry) sub = null } }
            if (sub == null) {
                BottomNav(
                    listOf(
                        Triple("خانه", Icons.Outlined.Home, tab == Tab.Home), Triple("کالاها", Icons.Outlined.Inventory2, tab == Tab.Items),
                        Triple("گزارش‌ها", Icons.Outlined.BarChart, tab == Tab.Report), Triple("تنظیمات", Icons.Outlined.Tune, tab == Tab.Settings)
                    )
                ) { tab = Tab.values()[it] }
            }
        }
    }
}

/* ------------------------------------------------------------------ home */
@Composable
private fun QuickTile(modifier: Modifier, icon: ImageVector, label: String, tone: Tone, onClick: () -> Unit) {
    val t = LocalTk.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(t.s).border(1.dp, t.line, shape).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBubble(icon, tone)
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = t.ink)
    }
}

@Composable
fun HomeScreen(
    store: Store, pcats: Set<String>, goProgress: () -> Unit, goImport: () -> Unit, goExport: () -> Unit,
    goTransfer: () -> Unit, goHistory: () -> Unit, goAction: (String) -> Unit, update: UpdateInfo?, onUpdate: () -> Unit, goAdd: () -> Unit
) {
    val t = LocalTk.current
    val all = store.items
    val total = all.size
    val counted = all.count { it.finalQty != null }
    val shortN = all.count { (it.finalQty ?: it.system) < it.system - 1e-9 }
    val surN = all.count { (it.finalQty ?: it.system) > it.system + 1e-9 }
    val recountN = all.count { it.first != null && it.second == null && abs(it.first!! - it.system) > 1e-9 }
    val pScope = all.filter { it.category in pcats }
    val pDone = pScope.count { it.finalQty != null }
    val pct = if (pScope.isEmpty()) 0f else pDone.toFloat() / pScope.size

    Column(Modifier.fillMaxSize()) {
        AppBar("انبارگردانی", subtitle = "${store.warehouse} · ${Jalali.monthYear()}", logo = true, actions = { IconBtn(Icons.Outlined.History, goHistory) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (update != null) {
                Panel(onClick = onUpdate) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Outlined.SystemUpdate, Tone.Ok)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("نسخه‌ی جدید ${fa(update.version)} موجود است", fontWeight = FontWeight.Bold, color = t.ink)
                            Text("برای دیدن تغییرات و دانلود لمس کنید", fontSize = 12.sp, color = t.mut)
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = t.mut, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (total == 0) {
                Panel {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconBubble(Icons.Outlined.FileUpload, Tone.Pri, 56.dp)
                        Text("هنوز اطلاعاتی وارد نشده است", fontWeight = FontWeight.Bold, color = t.ink)
                        Text("برای شروع، فایل اکسل کالاها را وارد کنید یا اطلاعات را از موبایل دیگر دریافت کنید.", fontSize = 13.sp, color = t.mut, textAlign = TextAlign.Center)
                        PrimaryButton("ورود از اکسل", goImport, icon = Icons.Outlined.FileUpload)
                        SecondaryButton("افزودن دستی کالا", goAdd, icon = Icons.Outlined.Add)
                    }
                }
            }
            Panel(onClick = goProgress) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("پیشرفت انبارگردانی", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = t.ink)
                        Text(if (pcats.isEmpty()) "انتخاب دسته‌ها" else "${faI(pcats.size)} دسته", color = t.pri, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = t.pri, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${faI((pct * 100).toInt())}٪", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
                    Spacer(Modifier.height(10.dp))
                    Track(pct)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (pcats.isEmpty()) "برای دیدن پیشرفت، دسته‌های موردنظر را انتخاب کنید"
                        else "${faI(pDone)} از ${faI(pScope.size)} کالا در دسته‌های انتخاب‌شده شمارش شده",
                        fontSize = 12.5.sp, color = t.mut
                    )
                }
            }
            Panel {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    for ((i, k) in listOf(Triple("کل کالاها", faI(total), t.ink), Triple("شمارش‌شده", faI(counted), t.pri), Triple("مغایرت", faI(shortN + surN), t.bad)).withIndex()) {
                        if (i > 0) VerticalDivider(color = t.line)
                        Column(Modifier.weight(1f).padding(14.dp)) {
                            Text(k.second, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = k.third)
                            Text(k.first, fontSize = 12.sp, color = t.mut)
                        }
                    }
                }
            }
            SectionTitle("نیاز به اقدام")
            Panel {
                ListRow(Icons.Outlined.ArrowDownward, "کالاهای کسری", "${faI(shortN)} قلم", Tone.Bad, onClick = { goAction("sh") }, trailing = { Text(faI(shortN), fontWeight = FontWeight.ExtraBold, color = t.bad) })
                ListRow(Icons.Outlined.ArrowUpward, "کالاهای اضافه", "${faI(surN)} قلم", Tone.Ok, onClick = { goAction("su") }, trailing = { Text(faI(surN), fontWeight = FontWeight.ExtraBold, color = t.ok) })
                ListRow(Icons.Outlined.Refresh, "نیاز به شمارش مجدد", "مغایرت در شمارش اول", Tone.Warn, onClick = { goAction("rc") }, trailing = { Text(faI(recountN), fontWeight = FontWeight.ExtraBold, color = t.warn) })
                ListRow(Icons.Outlined.Inventory2, "شمارش‌نشده", "${faI(total - counted)} قلم باقی مانده", Tone.Neutral, divider = false, onClick = { goAction("un") }, trailing = { Text(faI(total - counted), fontWeight = FontWeight.ExtraBold, color = t.mut) })
            }
            SectionTitle("دسترسی سریع")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Modifier.weight(1f), Icons.Outlined.FileUpload, "ورود اکسل", Tone.Pri, goImport)
                QuickTile(Modifier.weight(1f), Icons.Outlined.FileDownload, "خروجی", Tone.Ok, goExport)
                QuickTile(Modifier.weight(1f), Icons.Outlined.Share, "انتقال", Tone.Warn, goTransfer)
                QuickTile(Modifier.weight(1f), Icons.Outlined.Archive, "دوره‌ها", Tone.Neutral, goHistory)
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}

/* ------------------------------------------------------------------ items */
private val STATUS_OPTIONS = listOf("همه", "شمارش‌نشده", "شمارش‌شده", "دارای مغایرت", "مغایرت شمارش اول")
private val SORT_OPTIONS = listOf("نام (الف تا ی)", "کد", "موجودی سیستم", "بیشترین مغایرت", "شمارش‌نشده‌ها اول")

@Composable
private fun ItemRow(item: Item, onClick: () -> Unit) {
    val t = LocalTk.current
    val f = item.finalQty
    val d = f?.minus(item.system)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(formIcon(item.form), if (d == null) Tone.Neutral else diffTone(d))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.code.takeIf { it.isNotEmpty() }?.let { fa(it) }, item.form.takeIf { it.isNotEmpty() }, item.category.takeIf { it.isNotEmpty() })
                        .joinToString(" · ").ifEmpty { "سیستم ${faN(item.system)}" },
                    fontSize = 12.sp, color = t.mut, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (f == null || d == null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("—", color = t.mut)
                    Text("سیستم ${faN(item.system)}", fontSize = 11.sp, color = t.mut)
                }
            } else {
                val (fg, _) = toneColors(if (abs(d) < 1e-9) Tone.Neutral else diffTone(d))
                Column(horizontalAlignment = Alignment.End) {
                    Text(faN(f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
                    Text(if (abs(d) < 1e-9) "مطابق" else signed(d), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = fg)
                }
            }
        }
        HorizontalDivider(color = t.line)
    }
}

@Composable
fun ItemsScreen(
    store: Store, q: String, onQ: (String) -> Unit, status: Int, onStatus: (Int) -> Unit, sort: Int, onSort: (Int) -> Unit,
    cat: String?, onCat: (String?) -> Unit, form: String?, onForm: (String?) -> Unit,
    onScan: () -> Unit, onAdd: () -> Unit, onOpen: (String) -> Unit
) {
    val t = LocalTk.current
    var filter by remember { mutableStateOf(false) }
    var manageCat by remember { mutableStateOf(false) }
    var manageForm by remember { mutableStateOf(false) }
    val key = latin(q.trim())
    val collator = remember { java.text.Collator.getInstance(java.util.Locale("fa")) }
    val byName = remember { Comparator<Item> { x, y -> collator.compare(x.name, y.name) } }
    val shown by remember(key, cat, form, status, sort) {
        derivedStateOf {
            val list = store.items.filter {
                (cat == null || it.category == cat) && (form == null || it.form == form) &&
                    (key.isEmpty() || it.name.contains(key, true) || it.code.contains(key, true) || it.form.contains(key, true) || it.barcodes.any { b -> b.contains(key, true) }) &&
                    when (status) {
                        1 -> it.finalQty == null
                        2 -> it.finalQty != null
                        3 -> it.finalQty != null && abs(it.finalQty!! - it.system) > 1e-9
                        4 -> it.first != null && abs(it.first!! - it.system) > 1e-9
                        else -> true
                    }
            }
            when (sort) {
                1 -> list.sortedBy { it.code.ifEmpty { "\uFFFF" } }
                2 -> list.sortedByDescending { it.system }
                3 -> list.sortedByDescending { x -> x.finalQty?.let { v -> abs(v - x.system) } ?: -1.0 }
                4 -> list.sortedBy { if (it.finalQty == null) 0 else 1 }
                else -> list.sortedWith(byName)
            }
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 8 } }
    val badge = (if (cat != null) 1 else 0) + (if (form != null) 1 else 0) + (if (sort != 0) 1 else 0)

    Column(Modifier.fillMaxSize()) {
        AppBar("کالاها", actions = {
            IconBtn(Icons.Outlined.Add, onAdd)
            IconBtn(Icons.Outlined.FilterList, { filter = true }, badge = badge)
        })
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(q, onQ, "جستجوی نام، کد، بارکد یا شکل", onScan)
            Segmented(listOf("همه", "شمارش‌نشده", "شمارش‌شده", "مغایرت"), if (status == 4) 3 else status, onStatus)
            if (cat != null || form != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cat?.let { RemovableChip(it) { onCat(null) } }
                    form?.let { RemovableChip(it) { onForm(null) } }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("${faI(shown.size)} قلم از ${faI(store.items.size)}", Modifier.weight(1f), fontSize = 12.sp, color = t.mut)
                Text("مرتب‌سازی: ${SORT_OPTIONS[sort]}", fontSize = 12.sp, color = t.mut)
            }
            if (store.items.isEmpty()) {
                EmptyState(Icons.Outlined.Inventory2, "هنوز کالایی وارد نشده است.\nاز اکسل وارد کنید یا کالا را دستی اضافه کنید.")
                PrimaryButton("افزودن کالا", onAdd, icon = Icons.Outlined.Add)
            }
            else if (shown.isEmpty()) EmptyState(Icons.Outlined.Search, "موردی پیدا نشد")
            else Box(Modifier.weight(1f)) {
                Panel(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 84.dp)) {
                        items(shown, key = { it.key }) { item -> ItemRow(item) { onOpen(item.key) } }
                    }
                }
                if (showTop) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(12.dp).size(44.dp).clip(CircleShape).background(t.s).border(1.dp, t.line, CircleShape)
                            .clickable { scope.launch { listState.animateScrollToItem(0) } },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.ArrowUpward, null, tint = t.ink) }
                }
            }
        }
    }

    if (filter) {
        Sheet({ filter = false }, "فیلتر و ترتیب") {
            SelectField("دسته", cat ?: "همه دسته‌ها", store.categories.toList(), "همه دسته‌ها") { i -> onCat(if (i < 0) null else store.categories[i]) }
            SelectField("شکل دارویی", form ?: "همه شکل‌ها", store.forms.toList(), "همه شکل‌ها") { i -> onForm(if (i < 0) null else store.forms[i]) }
            SelectField("مرتب‌سازی", SORT_OPTIONS[sort], SORT_OPTIONS) { i -> onSort(if (i < 0) 0 else i) }
            SelectField("وضعیت", STATUS_OPTIONS[status], STATUS_OPTIONS) { i -> onStatus(if (i < 0) 0 else i) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GhostButton("مدیریت دسته‌ها", { filter = false; manageCat = true })
                GhostButton("مدیریت شکل‌ها", { filter = false; manageForm = true })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("پاک‌کردن", { onCat(null); onForm(null); onSort(0); onStatus(0) }, Modifier.weight(1f))
                PrimaryButton("اعمال", { filter = false }, Modifier.weight(1f))
            }
        }
    }
    if (manageCat) ManageSheet(
        "دسته‌ها", "دسته", store.categories.toList(), shown.size, { manageCat = false },
        { store.addCategory(it) },
        { o, n -> store.renameCategory(o, n).also { ok -> if (ok && cat == o) onCat(n) } },
        { store.deleteCategory(it); if (cat == it) onCat(null) },
        { c -> shown.forEach { store.setCategory(it.key, c) } }
    )
    if (manageForm) ManageSheet(
        "شکل‌های دارویی", "شکل", store.forms.toList(), shown.size, { manageForm = false },
        { store.addForm(it) },
        { o, n -> store.renameForm(o, n).also { ok -> if (ok && form == o) onForm(n) } },
        { store.deleteForm(it); if (form == it) onForm(null) },
        { f -> shown.forEach { store.setForm(it.key, f) } }
    )
}

@Composable
fun ManageSheet(
    title: String, noun: String, names: List<String>, shownCount: Int, onDismiss: () -> Unit,
    onAdd: (String) -> Unit, onEdit: (String, String) -> Boolean, onDelete: (String) -> Unit, onAssign: ((String) -> Unit)?
) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    var nn by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    Sheet(onDismiss, title) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(nn, { nn = it }, hint = "$noun جدید", modifier = Modifier.weight(1f))
            PrimaryButton("افزودن", { onAdd(nn); nn = "" }, Modifier.width(96.dp))
        }
        if (onAssign != null) Text("کالاها را با جستجو یا فیلتر محدود کنید، سپس «افزودن نتایج» را بزنید (${faI(shownCount)} قلم فعلی).", fontSize = 12.sp, color = t.mut)
        if (names.isEmpty()) EmptyState(Icons.Outlined.Sell, "هنوز $noun‌ای ساخته نشده است")
        else Panel {
            names.forEachIndexed { i, c ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = t.ink)
                        IconBtn(Icons.Outlined.Edit, { editText = c; editing = c })
                        IconBtn(Icons.Outlined.Delete, { onDelete(c) }, tint = t.bad)
                    }
                    if (onAssign != null) GhostButton("افزودن نتایج جستجوی فعلی به این $noun", { onAssign(c) })
                }
                if (i < names.lastIndex) HorizontalDivider(color = t.line)
            }
        }
    }
    editing?.let { old ->
        AppDialog({ editing = null }, "ویرایش نام $noun") {
            AppField(editText, { editText = it })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("ذخیره", {
                    if (onEdit(old, editText.trim())) editing = null
                    else Toast.makeText(ctx, "نام خالی یا تکراری است", Toast.LENGTH_SHORT).show()
                }, Modifier.weight(1f))
                SecondaryButton("انصراف", { editing = null }, Modifier.weight(1f))
            }
        }
    }
}

/* ------------------------------------------------------------------ settings */
@Composable
fun SettingsScreen(store: Store, goHistory: () -> Unit, goSend: () -> Unit, goReceive: () -> Unit, goImport: () -> Unit, onCheckUpdate: () -> Unit, onAddItem: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    val ver = remember { Updater.installed(ctx).first }
    var confirm by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var renameWh by remember { mutableStateOf(false) }
    var manageCat by remember { mutableStateOf(false) }
    var manageForm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppBar("تنظیمات")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("انبار")
            Panel {
                ListRow(Icons.Outlined.CalendarMonth, "نام انبار / دوره", store.warehouse, onClick = { renameWh = true })
                ListRow(Icons.Outlined.Layers, "دسته‌ها", "${faI(store.categories.size)} دسته", onClick = { manageCat = true })
                ListRow(Icons.Outlined.Sell, "شکل‌های دارویی", "${faI(store.forms.size)} شکل", divider = false, onClick = { manageForm = true })
            }
            SectionTitle("داده‌ها")
            Panel {
                ListRow(Icons.Outlined.Add, "افزودن کالای جدید", "ثبت دستی با نام، کد و چند بارکد", onClick = onAddItem)
                ListRow(Icons.Outlined.FileUpload, "ورود از اکسل", onClick = goImport)
                ListRow(Icons.Outlined.Archive, "بایگانی دوره فعال", "ذخیره‌ی نتیجه‌ی شمارش", onClick = { archive = true })
                ListRow(Icons.Outlined.History, "دوره‌های قبل", "${faI(store.periods.size)} دوره", divider = false, onClick = goHistory)
            }
            SectionTitle("انتقال بین گوشی‌ها")
            Panel {
                ListRow(Icons.Outlined.NorthEast, "ارسال اطلاعات", "با Wi‑Fi یا Hotspot", Tone.Ok, onClick = goSend)
                ListRow(Icons.Outlined.SouthWest, "دریافت اطلاعات", null, Tone.Ok, divider = false, onClick = goReceive)
            }
            SectionTitle("به‌روزرسانی")
            Panel {
                ListRow(Icons.Outlined.SystemUpdate, "بررسی نسخه‌ی جدید", "نسخه‌ی فعلی ${fa(ver)}", Tone.Ok, onClick = onCheckUpdate)
                ListRow(Icons.Outlined.Link, "صفحه‌ی دانلود برنامه", "آخرین نسخه در GitHub", Tone.Neutral, divider = false, onClick = { Updater.open(ctx, RELEASES_PAGE) })
            }
            SectionTitle("درباره")
            Panel {
                ListRow(Icons.Outlined.Code, "توسعه‌دهنده", "حسین حسین‌زاده", Tone.Neutral, onClick = { about = true })
                ListRow(Icons.Outlined.Info, "انبارگردانی", "نسخه ${fa(ver)}", Tone.Neutral, divider = false)
            }
            Spacer(Modifier.height(4.dp))
            DangerButton("پاک‌کردن همه شمارش‌ها", { confirm = true }, icon = Icons.Outlined.Delete)
            Spacer(Modifier.height(16.dp))
        }
    }

    if (manageCat) ManageSheet(
        "دسته‌ها", "دسته", store.categories.toList(), 0, { manageCat = false }, { store.addCategory(it) },
        { o, n -> store.renameCategory(o, n) }, { store.deleteCategory(it) }, null
    )
    if (manageForm) ManageSheet(
        "شکل‌های دارویی", "شکل", store.forms.toList(), 0, { manageForm = false }, { store.addForm(it) },
        { o, n -> store.renameForm(o, n) }, { store.deleteForm(it) }, null
    )
    if (renameWh) {
        var name by remember { mutableStateOf(store.warehouse) }
        AppDialog({ renameWh = false }, "نام انبار / دوره") {
            AppField(name, { name = it })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("ذخیره", { if (name.isNotBlank()) { store.warehouse = name.trim(); store.save() }; renameWh = false }, Modifier.weight(1f))
                SecondaryButton("انصراف", { renameWh = false }, Modifier.weight(1f))
            }
        }
    }
    if (archive) {
        var plabel by remember { mutableStateOf("${store.warehouse} - ${Jalali.monthYear()}") }
        fun doArchive(reset: Boolean) {
            val ok = store.archive(plabel, reset)
            Toast.makeText(ctx, if (ok) "دوره بایگانی شد" else "هنوز کالایی شمارش نشده است", Toast.LENGTH_SHORT).show()
            archive = false
        }
        AppDialog({ archive = false }, "بایگانی دوره فعال") {
            AppField(plabel, { plabel = it }, label = "نام دوره")
            Text("نتیجه‌ی شمارش فعلی (موجودی سیستم، شمارش اول و مجدد) برای مرور بعدی ذخیره می‌شود.", fontSize = 12.sp, color = t.mut)
            PrimaryButton("بایگانی و شروع دوره جدید", { doArchive(true) })
            SecondaryButton("فقط بایگانی", { doArchive(false) })
            GhostButton("انصراف", { archive = false }, Modifier.fillMaxWidth())
        }
    }
    if (about) {
        AppDialog({ about = false }, "توسعه‌دهنده") {
            Text("حسین حسین‌زاده", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
            Text("\u200EHzadeh007@gmail.com", color = t.pri, fontSize = 15.sp)
            Text("انبارگردانی – مدیریت هوشمند انبار • نسخه ${fa(ver)}", fontSize = 12.sp, color = t.mut)
            PrimaryButton("ارسال ایمیل", {
                try {
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:Hzadeh007@gmail.com")).putExtra(Intent.EXTRA_SUBJECT, "انبارگردانی – بازخورد"))
                } catch (e: Exception) {
                    Toast.makeText(ctx, "برنامه‌ی ایمیلی پیدا نشد", Toast.LENGTH_SHORT).show()
                }
            }, icon = Icons.Outlined.Mail)
            GhostButton("بستن", { about = false }, Modifier.fillMaxWidth())
        }
    }
    if (confirm) {
        AppDialog({ confirm = false }, "همه شمارش‌ها پاک شود؟") {
            Text("شمارش اول و مجدد همه‌ی کالاها پاک می‌شود. موجودی سیستم تغییر نمی‌کند.", color = t.mut)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DangerButton("پاک شود", { store.resetCounts(); confirm = false }, Modifier.weight(1f))
                SecondaryButton("انصراف", { confirm = false }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun UpdateDialog(info: UpdateInfo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    val main = remember { Handler(Looper.getMainLooper()) }
    var state by remember { mutableIntStateOf(0) } // 0 = ready, 1 = downloading, 2 = downloaded, 3 = error
    var pct by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    val cancel = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    fun start() {
        state = 1; pct = 0; cancel.set(false)
        Thread {
            val r = Updater.download(ctx, info.downloadUrl, { p -> main.post { pct = p } }, { cancel.get() })
            main.post {
                val f = r.first
                if (f != null) { file = f; state = 2; Updater.install(ctx, f) }
                else if (cancel.get()) state = 0
                else { err = r.second ?: "دانلود ناموفق بود"; state = 3 }
            }
        }.start()
    }
    fun close() { cancel.set(true); onClose() }

    AppDialog({ close() }, "نسخه‌ی جدید موجود است") {
        Text("نسخه ${fa(info.version)}", fontWeight = FontWeight.Bold, color = t.ink)
        if (info.notes.isNotBlank()) Text(info.notes.trim().take(500), fontSize = 12.5.sp, color = t.mut)
        when (state) {
            1 -> {
                Track(pct / 100f)
                Text("در حال دانلود… ${faI(pct)}٪", fontSize = 12.5.sp, color = t.mut)
                SecondaryButton("لغو دانلود", { cancel.set(true) })
            }
            2 -> {
                StatusBanner(Tone.Ok, Icons.Outlined.CheckCircle, "دانلود کامل شد")
                PrimaryButton("نصب نسخه‌ی جدید", { file?.let { Updater.install(ctx, it) } }, icon = Icons.Outlined.SystemUpdate)
                Text("اگر پنجره‌ی نصب باز نشد، اجازه‌ی «نصب از این منبع» را بدهید و دوباره «نصب» را بزنید.", fontSize = 11.5.sp, color = t.mut)
            }
            3 -> {
                StatusBanner(Tone.Bad, Icons.Outlined.Warning, err)
                PrimaryButton("تلاش دوباره", { start() }, icon = Icons.Outlined.Refresh)
            }
            else -> {
                if (info.hasApk) PrimaryButton("دانلود و نصب", { start() }, icon = Icons.Outlined.FileDownload)
                SecondaryButton("صفحه‌ی دانلود", { Updater.open(ctx, info.pageUrl) })
            }
        }
        GhostButton(if (state == 1) "بستن (دانلود لغو می‌شود)" else "بعداً", { close() }, Modifier.fillMaxWidth())
    }
}
