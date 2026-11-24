package com.hesabgara.anbargardan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** GitHub repository that publishes the APK files (Releases). Change this if the repository name changes. */
const val UPDATE_REPO = "hosseinzadeh007/anabargardani"
const val RELEASES_PAGE = "https://github.com/$UPDATE_REPO/releases/latest"

data class UpdateInfo(val version: String, val code: Long, val notes: String, val downloadUrl: String, val pageUrl: String, val hasApk: Boolean)

object Updater {
    /** (versionName, versionCode) of the installed app. */
    fun installed(ctx: Context): Pair<String, Long> {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return (p.versionName ?: "1.0") to PackageInfoCompat.getLongVersionCode(p)
    }

    /** Blocking – call from a background thread. Returns (newer release or null, error message or null). */
    fun check(ctx: Context): Pair<UpdateInfo?, String?> {
        return try {
            val c = URL("https://api.github.com/repos/$UPDATE_REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Anbargardani")
            val status = c.responseCode
            if (status == 404) return null to "هنوز نسخه‌ی منتشرشده‌ای پیدا نشد"
            if (status != 200) return null to "خطای سرور ($status)"
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name")
            val code = Regex("(\\d+)\\D*$").find(tag)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val page = o.optString("html_url", RELEASES_PAGE)
            var url = page
            var hasApk = false
            val assets = o.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url", page); hasApk = true; break }
                }
            }
            val info = UpdateInfo(tag.trimStart('v'), code, o.optString("body"), url, page, hasApk)
            (if (code > installed(ctx).second) info else null) to null
        } catch (e: Exception) {
            null to "بررسی ناموفق بود: ${e.message}"
        }
    }

    /** Blocking – background thread. Downloads the APK into the app cache; returns (file, error). */
    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit, cancelled: () -> Boolean): Pair<File?, String?> {
        return try {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "Anbargardani.apk")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "Anbargardani")
            if (c.responseCode != 200) return null to "خطای سرور (${c.responseCode})"
            val total = c.contentLengthLong
            var done = 0L
            var last = -1
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        if (cancelled()) { out.delete(); return null to null }
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) { last = p; onProgress(p) }
                        }
                    }
                }
            }
            if (out.length() < 100_000) return null to "فایل دانلودشده معتبر نیست"
            out to null
        } catch (e: Exception) {
            null to "دانلود ناموفق بود: ${e.message}"
        }
    }

    /** Opens the system installer. Returns false when the user must first allow installs from this app. */
    fun install(ctx: Context, f: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) { }
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    fun open(ctx: Context, url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }
}
