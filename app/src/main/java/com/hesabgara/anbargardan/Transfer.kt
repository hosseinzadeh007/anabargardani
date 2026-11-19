package com.hesabgara.anbargardan

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

data class PeerCode(val host: String, val port: Int, val pin: String)

/** Direct phone-to-phone transfer over the local network (same Wi-Fi, or one phone's hotspot). No internet needed. */
object P2p {
    private val codeRx = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){3}):(\\d{1,5})-(\\d{4})$")

    /** Connection code format: ip:port-pin  (also the QR content). */
    fun parse(raw: String): PeerCode? {
        val m = codeRx.matchEntire(latin(raw.trim())) ?: return null
        val port = m.groupValues[2].toInt()
        if (port !in 1..65535) return null
        return PeerCode(m.groupValues[1], port, m.groupValues[3])
    }

    fun localIp(): String? {
        val cands = ArrayList<Pair<String, String>>()
        try {
            val all = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in all) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) {
                    if (a is Inet4Address && a.isSiteLocalAddress) cands.add(ni.name to (a.hostAddress ?: continue))
                }
            }
        } catch (e: Exception) { }
        return cands.firstOrNull { it.first.startsWith("wlan") || it.first.startsWith("ap") || it.first.startsWith("swlan") }?.second
            ?: cands.firstOrNull()?.second
    }

    /** Blocking: call from a background thread. Returns null on success, otherwise an error message. */
    fun send(code: PeerCode, json: String): String? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(code.host, code.port), 5000)
            s.soTimeout = 20000
            val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
            val inp = DataInputStream(BufferedInputStream(s.getInputStream()))
            out.writeUTF("DARAK1"); out.writeUTF(code.pin); out.flush()
            if (inp.readUTF() != "READY") "رمز کد اشتباه است"
            else {
                val bytes = json.toByteArray(Charsets.UTF_8)
                out.writeInt(bytes.size); out.write(bytes); out.flush()
                if (inp.readUTF() == "OK") null else "گیرنده داده را نپذیرفت"
            }
        }
    } catch (e: Exception) {
        "اتصال برقرار نشد (هر دو گوشی در یک Wi-Fi باشند): ${e.message}"
    }
}

class Receiver(private val onData: (String) -> Unit) {
    val pin: String = (1000..9999).random().toString()
    @Volatile private var running = false
    private var server: ServerSocket? = null

    fun start(): Int {
        val s = ServerSocket(0)
        server = s
        running = true
        Thread {
            try {
                while (running) {
                    val c = s.accept()
                    try { handle(c) } catch (e: Exception) { } finally { try { c.close() } catch (e: Exception) { } }
                }
            } catch (e: Exception) { /* server closed */ }
        }.start()
        return s.localPort
    }

    private fun handle(c: Socket) {
        c.soTimeout = 20000
        val inp = DataInputStream(BufferedInputStream(c.getInputStream()))
        val out = DataOutputStream(c.getOutputStream())
        if (inp.readUTF() != "DARAK1" || inp.readUTF() != pin) { out.writeUTF("ERR"); out.flush(); return }
        out.writeUTF("READY"); out.flush()
        val len = inp.readInt()
        if (len <= 0 || len > 50_000_000) { out.writeUTF("ERR"); out.flush(); return }
        val buf = ByteArray(len)
        inp.readFully(buf)
        out.writeUTF("OK"); out.flush()
        onData(String(buf, Charsets.UTF_8))
    }

    fun stop() {
        running = false
        try { server?.close() } catch (e: Exception) { }
    }
}

@Composable
fun IncomingDialog(store: Store, text: String, info: BackupInfo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    fun done(merge: Boolean) {
        val n = try { store.importBackup(text, merge) } catch (e: Exception) { -1 }
        Toast.makeText(ctx, if (n < 0) "فایل خراب است" else "${faI(n)} مورد به‌روز شد", Toast.LENGTH_SHORT).show()
        onClose()
    }
    AppDialog(onClose, "اطلاعات دریافت شد") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Outlined.FileDownload, Tone.Ok)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(info.warehouse.ifEmpty { "—" }, fontWeight = FontWeight.Bold, color = t.ink)
                Text("${faI(info.total)} کالا · ${faI(info.counted)} شمارش‌شده", fontSize = 12.sp, color = t.mut)
            }
        }
        Text("«ادغام»: شمارش‌های دریافتی روی کالاهای مشترک ثبت می‌شود و کالاهای جدید اضافه می‌شوند. موجودی سیستم این گوشی تغییر نمی‌کند.", fontSize = 12.sp, color = t.mut)
        PrimaryButton("ادغام شمارش‌ها", { done(true) })
        DangerButton("جایگزینی کامل (اطلاعات این گوشی پاک می‌شود)", { done(false) })
        GhostButton("انصراف", onClose, Modifier.fillMaxWidth())
    }
}

@Composable
fun ReceiveScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    val main = remember { Handler(Looper.getMainLooper()) }
    var incoming by remember { mutableStateOf<Pair<String, BackupInfo>?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val info = store.peekBackup(text)
                if (info == null) Toast.makeText(ctx, "فایل معتبر انبارگردانی نیست", Toast.LENGTH_LONG).show()
                else incoming = text to info
            } catch (e: Exception) {
                Toast.makeText(ctx, "خطا در خواندن فایل: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    DisposableEffect(Unit) {
        val ip = P2p.localIp()
        val rx = Receiver { text ->
            main.post {
                val info = store.peekBackup(text)
                if (info == null) Toast.makeText(ctx, "داده‌ی دریافتی معتبر نیست", Toast.LENGTH_LONG).show()
                else incoming = text to info
            }
        }
        if (ip == null) err = "به Wi-Fi وصل نیستید و Hotspot هم روشن نیست."
        else try {
            val port = rx.start()
            val c = "$ip:$port-${rx.pin}"
            code = c
            qr = BarcodeEncoder().encodeBitmap(c, BarcodeFormat.QR_CODE, 700, 700)
        } catch (e: Exception) {
            err = "راه‌اندازی دریافت ممکن نشد: ${e.message}"
        }
        onDispose { rx.stop() }
    }

    Column(Modifier.fillMaxSize()) {
        AppBar("دریافت اطلاعات", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "هر دو گوشی را به یک Wi-Fi وصل کنید (یا روی یکی Hotspot روشن کنید و گوشی دیگر را به آن وصل کنید). سپس در گوشی فرستنده «ارسال اطلاعات» را بزنید و این کد را اسکن کنید.",
                fontSize = 13.sp, color = t.mut, textAlign = TextAlign.Center
            )
            qr?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp))
            }
            code?.let {
                Text("کد اتصال", fontSize = 12.sp, color = t.mut)
                Text("\u200E$it", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = t.pri)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Wifi, null, tint = t.mut, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("منتظر اتصال فرستنده…", fontSize = 12.5.sp, color = t.mut)
                }
            }
            err?.let { StatusBanner(Tone.Bad, Icons.Outlined.Warning, it) }
            GhostButton("دریافت از فایل (شیرایت، بلوتوث، ...)", { picker.launch(arrayOf("*/*")) })
        }
    }
    incoming?.let { (text, info) -> IncomingDialog(store, text, info) { incoming = null } }
}

@Composable
fun SendScreen(store: Store, onScan: ((String) -> Unit) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTk.current
    val main = remember { Handler(Looper.getMainLooper()) }
    var codeText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    val total = store.items.size
    val counted = store.items.count { it.finalQty != null }

    fun go(raw: String) {
        val c = P2p.parse(raw)
        if (c == null) { ok = false; msg = "کد اتصال معتبر نیست (مثل 192.168.1.5:45123-4821)"; return }
        busy = true; msg = null
        val json = store.exportJson()
        Thread {
            val e = P2p.send(c, json)
            main.post { busy = false; ok = e == null; msg = e ?: "اطلاعات با موفقیت ارسال شد" }
        }.start()
    }

    Column(Modifier.fillMaxSize()) {
        AppBar("ارسال اطلاعات", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("در گوشی گیرنده «دریافت اطلاعات» را بزنید تا کد (QR) نمایش داده شود. هر دو گوشی باید در یک Wi-Fi یا Hotspot باشند.", fontSize = 13.sp, color = t.mut)
            Panel {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Outlined.Inventory2, Tone.Pri)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("${faI(total)} کالا", fontWeight = FontWeight.Bold, color = t.ink)
                        Text("${faI(counted)} شمارش‌شده برای ارسال", fontSize = 12.sp, color = t.mut)
                    }
                }
            }
            PrimaryButton("اسکن کد گیرنده و ارسال", { onScan { go(it) } }, icon = Icons.Outlined.QrCodeScanner, enabled = !busy)
            AppField(codeText, { codeText = it }, label = "یا کد اتصال را بنویسید", hint = "192.168.1.5:45123-4821")
            SecondaryButton("ارسال با کد", { go(codeText) }, enabled = !busy && codeText.isNotBlank())
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = t.pri)
            msg?.let { StatusBanner(if (ok) Tone.Ok else Tone.Bad, if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Warning, it) }
            GhostButton("ارسال به‌صورت فایل (شیرایت، بلوتوث، ...)", { Export.shareBackup(ctx, store.exportJson()) })
            Spacer(Modifier.height(16.dp))
        }
    }
}
