package com.hesabgara.anbargardan

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/* ---------------------------------------------------------------- design tokens */
@Immutable
class Tk(
    val bg: Color, val s: Color, val s2: Color, val ink: Color, val mut: Color, val line: Color,
    val pri: Color, val pris: Color, val ok: Color, val oks: Color, val bad: Color, val bads: Color,
    val warn: Color, val warns: Color, val acc: Color
)

val LightTk = Tk(
    bg = Color(0xFFF5F7FB), s = Color.White, s2 = Color(0xFFF0F3F9), ink = Color(0xFF0F172A), mut = Color(0xFF64748B), line = Color(0xFFE4E8F0),
    pri = Color(0xFF1E5BD8), pris = Color(0xFFE8EFFF), ok = Color(0xFF15803D), oks = Color(0xFFE5F5EB), bad = Color(0xFFDC2626), bads = Color(0xFFFDECEC),
    warn = Color(0xFFB45309), warns = Color(0xFFFFF3E0), acc = Color(0xFFF59E0B)
)
val DarkTk = Tk(
    bg = Color(0xFF0B1020), s = Color(0xFF141B2D), s2 = Color(0xFF1B2338), ink = Color(0xFFE8EDF7), mut = Color(0xFF94A3B8), line = Color(0xFF25304A),
    pri = Color(0xFF6B9BFF), pris = Color(0xFF1A2A52), ok = Color(0xFF4ADE80), oks = Color(0xFF12301F), bad = Color(0xFFF87171), bads = Color(0xFF3A1C20),
    warn = Color(0xFFFBBF24), warns = Color(0xFF3A2A10), acc = Color(0xFFF59E0B)
)
val LocalTk = staticCompositionLocalOf { LightTk }

@Composable
fun AnbarTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val t = if (dark) DarkTk else LightTk
    val scheme = if (dark) darkColorScheme(
        primary = t.pri, background = t.bg, surface = t.s, onSurface = t.ink, onBackground = t.ink,
        surfaceVariant = t.s2, secondaryContainer = t.pris, onSecondaryContainer = t.pri, outline = t.line
    ) else lightColorScheme(
        primary = t.pri, background = t.bg, surface = t.s, onSurface = t.ink, onBackground = t.ink,
        surfaceVariant = t.s2, secondaryContainer = t.pris, onSecondaryContainer = t.pri, outline = t.line
    )
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window
        if (w != null) {
            w.statusBarColor = t.bg.toArgb()
            w.navigationBarColor = t.s.toArgb()
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalTk provides t) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                labelLarge = TextStyle(fontSize = 14.sp)
            ),
            content = content
        )
    }
}

enum class Tone { Pri, Ok, Bad, Warn, Neutral }

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val k = LocalTk.current
    return when (tone) {
        Tone.Pri -> k.pri to k.pris
        Tone.Ok -> k.ok to k.oks
        Tone.Bad -> k.bad to k.bads
        Tone.Warn -> k.warn to k.warns
        Tone.Neutral -> k.mut to k.s2
    }
}

fun diffTone(d: Double): Tone = if (d < -1e-9) Tone.Bad else if (d > 1e-9) Tone.Ok else Tone.Pri

/* ---------------------------------------------------------------- components */
@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit, tint: Color? = null, badge: Int = 0) {
    val t = LocalTk.current
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint ?: t.ink, modifier = Modifier.size(22.dp))
        if (badge > 0) {
            Box(
                Modifier.align(Alignment.TopStart).padding(4.dp).defaultMinSize(16.dp, 16.dp).background(t.pri, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Text(faI(badge), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
    }
}

@Composable
fun AppBar(
    title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, logo: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val t = LocalTk.current
    Row(
        Modifier.fillMaxWidth().background(t.bg).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconBtn(Icons.AutoMirrored.Outlined.ArrowBack, onBack)
        if (logo) {
            Image(painterResource(R.drawable.logo), null, Modifier.padding(horizontal = 6.dp).size(40.dp).clip(RoundedCornerShape(11.dp)))
        }
        Column(Modifier.weight(1f).padding(horizontal = if (onBack == null && !logo) 8.dp else 4.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = t.mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTk.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(t.s).border(1.dp, t.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}

@Composable
fun IconBubble(icon: ImageVector, tone: Tone = Tone.Pri, size: Dp = 38.dp) {
    val (fg, bg) = toneColors(tone)
    Box(Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LocalTk.current.mut, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
fun ListRow(
    icon: ImageVector, title: String, subtitle: String? = null, tone: Tone = Tone.Pri, divider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null, chevron: Boolean = true
) {
    val t = LocalTk.current
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, tone)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = t.mut, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing?.invoke()
            if (onClick != null && chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = t.mut, modifier = Modifier.size(18.dp))
        }
        if (divider) HorizontalDivider(color = t.line)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val t = LocalTk.current
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = t.pri, contentColor = Color.White), elevation = null
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val t = LocalTk.current
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, t.line), colors = ButtonDefaults.outlinedButtonColors(containerColor = t.s, contentColor = t.pri)
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val t = LocalTk.current
    Button(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = t.bads, contentColor = t.bad), elevation = null
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    val t = LocalTk.current
    TextButton(onClick = onClick, modifier = modifier) { Text(text, color = if (danger) t.bad else t.pri, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp) }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val t = LocalTk.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.s2).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(9.dp)).background(if (on) t.s else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) { Text(o, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) t.ink else t.mut, maxLines = 1) }
        }
    }
}

@Composable
fun RemovableChip(text: String, onRemove: () -> Unit) {
    val t = LocalTk.current
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(t.pris).clickable(onClick = onRemove).padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = t.pri)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.Close, null, tint = t.pri, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, onScan: () -> Unit) {
    val t = LocalTk.current
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = t.ink), cursorBrush = SolidColor(t.pri),
        modifier = Modifier.fillMaxWidth().height(48.dp),
        decorationBox = { inner ->
            Row(Modifier.fillMaxSize().clip(shape).background(t.s).border(1.5.dp, t.line, shape).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = t.mut, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(hint, color = t.mut, fontSize = 14.sp); inner() }
                IconBtn(Icons.Outlined.QrCodeScanner, onScan, tint = t.pri)
            }
        }
    )
}

@Composable
fun AppField(value: String, onChange: (String) -> Unit, label: String? = null, hint: String = "", modifier: Modifier = Modifier) {
    val t = LocalTk.current
    val shape = RoundedCornerShape(14.dp)
    Column(modifier) {
        if (label != null) Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.mut, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = t.ink), cursorBrush = SolidColor(t.pri),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize().clip(shape).background(t.s).border(1.5.dp, t.line, shape).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(hint, color = t.mut, fontSize = 14.sp)
                    inner()
                }
            }
        )
    }
}

/** Dropdown picker: options are labels; onPick receives the chosen index (or -1 for the "all/none" entry). */
@Composable
fun SelectField(label: String, valueText: String, options: List<String>, allLabel: String? = null, onPick: (Int) -> Unit) {
    val t = LocalTk.current
    val shape = RoundedCornerShape(14.dp)
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.mut, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(shape).background(t.s).border(1.5.dp, t.line, shape).clickable { open = true }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(valueText, Modifier.weight(1f), fontSize = 14.sp, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = t.mut)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (allLabel != null) DropdownMenuItem(text = { Text(allLabel) }, onClick = { onPick(-1); open = false })
                options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(i); open = false }) }
            }
        }
    }
}

@Composable
fun Track(p: Float, modifier: Modifier = Modifier, color: Color? = null) {
    val t = LocalTk.current
    val a by animateFloatAsState(p.coerceIn(0f, 1f), tween(600), label = "track")
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(t.s2)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(a).background(color ?: t.pri, RoundedCornerShape(4.dp)))
    }
}

@Composable
fun CheckMark(on: Boolean) {
    val t = LocalTk.current
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(24.dp).clip(shape).background(if (on) t.pri else Color.Transparent).border(2.dp, if (on) t.pri else t.line, shape),
        contentAlignment = Alignment.Center
    ) { if (on) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    val t = LocalTk.current
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = t.mut.copy(alpha = 0.6f), modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = t.mut, textAlign = TextAlign.Center, fontSize = 14.sp)
    }
}

@Composable
fun StatusBanner(tone: Tone, icon: ImageVector, text: String) {
    val (fg, bg) = toneColors(tone)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BottomNav(items: List<Triple<String, ImageVector, Boolean>>, onSelect: (Int) -> Unit) {
    val t = LocalTk.current
    Column(Modifier.background(t.s)) {
        HorizontalDivider(color = t.line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            items.forEachIndexed { i, (label, icon, on) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSelect(i) }.padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.width(28.dp).height(3.dp).background(if (on) t.pri else Color.Transparent, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.height(7.dp))
                    Icon(icon, null, tint = if (on) t.pri else t.mut, modifier = Modifier.size(24.dp))
                    Text(label, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) t.pri else t.mut)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTk.current
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = t.bg, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (title != null) Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
            content()
        }
    }
}

@Composable
fun AppDialog(onDismiss: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTk.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = t.bg, shape = RoundedCornerShape(22.dp),
        title = { Text(title, fontWeight = FontWeight.ExtraBold, color = t.ink, fontSize = 17.sp) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = {}
    )
}
