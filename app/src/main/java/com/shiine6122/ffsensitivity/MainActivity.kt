package com.shiine6122.ffsensitivity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val SHIZUKU_CODE = 1001
    private val NOTIF_CODE = 1002
    private val CHANNEL = "ff_channel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        
        // Cấu hình WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true // Bật localStorage để lưu cài đặt
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")

        // Tạo kênh thông báo
        createChannel()

        // Yêu cầu quyền thông báo (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), NOTIF_CODE)
            }
        }

        // Yêu cầu quyền Shizuku khi mở app
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_CODE)
        }
    }

    // Hàm tạo kênh thông báo
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "FF Sensitivity", NotificationManager.IMPORTANCE_DEFAULT)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    // Hàm gửi thông báo
    private fun notify(sens: Int) {
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Icon mặc định
            .setContentTitle("FF Sensitivity")
            .setContentText("Đã áp dụng độ nhạy: $sens")
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, n)
    }

    // Hàm tính toán Downscale theo công thức
    private fun calcDS(sens: Int): String {
        if (sens <= 200) return "1.0000"
        val steps = (sens - 200).toDouble() / 10
        val ds = 1.0 + steps * 0.031265822784810126
        return String.format(java.util.Locale.US, "%.4f", if (ds > 3.5) 3.5 else ds)
    }

    // Hàm chạy Script Shell thông qua Shizuku
    private fun runShell(sens: Int) {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread { webView.evaluateJavascript("alert('Chưa cấp quyền Shizuku!');", null) }
            return
        }
        
        val ds = calcDS(sens)
        
        // Đoạn Script Shell của bạn
        val script = """
settings put system touchboost 1
settings put system touch_prediction 1
settings put system touch_response 1
settings put system touch_response_delay 0
settings put system touch_duration_threshold 0
settings put system touch_filter 0
settings put system touch_filter_level 0
settings put system touch_jitter_filter 0
settings put system touch_noise_filter 0
settings put system touch_precision_optimise 1
settings put system touch_slop 0
settings put system touch_swipe 0
settings put system touch_acceleration 1
settings put system touch_acceleration_factor 1
settings put system touch_glove_mode 1
settings put system touch_hover_enable 0
settings put system touch_self_calibration 1
settings put system show_touches 0
HZ=${'$'}(dumpsys display 2>/dev/null | grep -oE "vsyncRate [0-9]+" | head -1 | grep -oE "[0-9]+")
[ -z "${'$'}HZ" ] && HZ=90
cmd game set --mode 2 --downscale $ds --fps ${'$'}HZ com.dts.freefireth
cmd power set-fixed-performance-mode-enabled true 2>/dev/null
echo "OK"
        """.trimIndent()
        
        try {
            // Chạy lệnh qua Shizuku
            val p = Shizuku.newProcess(arrayOf("sh", "-c", script), null, null)
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (r.readLine().also { line = it } != null) sb.append(line).append("\n")
            p.waitFor()
            
            // Chạy xong thì gửi thông báo và báo về giao diện Web
            runOnUiThread {
                notify(sens)
                webView.evaluateJavascript("onApplySuccess()", null)
            }
        } catch (e: Exception) {
            runOnUiThread { webView.evaluateJavascript("alert('Lỗi: ${e.message}');", null) }
        }
    }

    // Cầu nối giữa JavaScript (HTML) và Kotlin (Native)
    inner class Bridge {
        @JavascriptInterface
        fun applySensitivity(sens: Int, ds: String) = runShell(sens)

        @JavascriptInterface
        fun requestShizukuPermission() {
            runOnUiThread {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(SHIZUKU_CODE)
                }
            }
        }
    }
}