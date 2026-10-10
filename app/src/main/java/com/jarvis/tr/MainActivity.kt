package com.jarvis.tr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var logText: TextView
    private lateinit var statusText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var settingsBtn: Button

    companion object {
        private const val REQ_AUDIO = 1001
        private const val REQ_NOTIF = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logText = findViewById(R.id.logText)
        statusText = findViewById(R.id.statusText)
        logScroll = findViewById(R.id.logScroll)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        settingsBtn = findViewById(R.id.settingsBtn)

        log("JARVIS başlatıldı")
        log("Başlamak için dokun")

        requestNotifPermission()

        startBtn.setOnClickListener {
            checkPermissionsAndStart()
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, VoiceService::class.java))
            statusText.text = "DURDURULDU"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.red))
            log("Servis durduruldu")
            Toast.makeText(this, "JARVIS durdu", Toast.LENGTH_SHORT).show()
        }

        settingsBtn.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIF
                )
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val audioOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!audioOk) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQ_AUDIO
            )
            return
        }

        if (!isAccessibilityEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Erişilebilirlik İzni Gerekli")
                .setMessage("Telefonunu kontrol edebilmem için JarvisTR'yi erişilebilirlik hizmetlerinde açman gerekiyor.\n\nAyarlar açılacak, listede JarvisTR Kontrolü'nü bulup AÇ.")
                .setPositiveButton("Ayarları Aç") { _, _ -> openAccessibilitySettings() }
                .setNegativeButton("İptal", null)
                .show()
            return
        }

        startVoiceService()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/${JarvisAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(service)
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "JarvisTR Kontrolü'nü bul ve AÇ", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ayarlar açılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusText.text = "DİNLİYORUM"
        statusText.setTextColor(ContextCompat.getColor(this, R.color.cyan))
        log("Dinleme başladı — konuşabilirsin")
        log("Örnek: 'Jarvis YouTube aç'")
        log("Örnek: 'Jarvis Rannee korku oyunları ara'")
        Toast.makeText(this, "JARVIS dinliyor", Toast.LENGTH_SHORT).show()
    }

    private fun showSettingsDialog() {
        val prefs = PrefsManager(this)
        val options = arrayOf(
            "Ses Tonu (Pitch): ${prefs.pitch}",
            "Konuşma Hızı (Rate): ${prefs.rate}",
            "Sesli Yanıt: ${if (prefs.voiceOn) "Açık" else "Kapalı"}",
            "Otomatik Yeniden Dinleme: ${if (prefs.autoRestart) "Açık" else "Kapalı"}"
        )
        AlertDialog.Builder(this)
            .setTitle("Ayarlar")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cyclePitch(prefs)
                    1 -> cycleRate(prefs)
                    2 -> {
                        prefs.voiceOn = !prefs.voiceOn
                        log("Sesli yanıt: ${if (prefs.voiceOn) "Açık" else "Kapalı"}")
                    }
                    3 -> {
                        prefs.autoRestart = !prefs.autoRestart
                        log("Oto yeniden dinleme: ${if (prefs.autoRestart) "Açık" else "Kapalı"}")
                    }
                }
            }
            .setPositiveButton("Kapat", null)
            .show()
    }

    private fun cyclePitch(prefs: PrefsManager) {
        val options = listOf(0.6f, 0.75f, 0.85f, 1.0f, 1.15f)
        val current = prefs.pitch
        val next = options.firstOrNull { it > current } ?: options.first()
        prefs.pitch = next
        log("Ses tonu: $next")
    }

    private fun cycleRate(prefs: PrefsManager) {
        val options = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f)
        val current = prefs.rate
        val next = options.firstOrNull { it > current } ?: options.first()
        prefs.rate = next
        log("Konuşma hızı: $next")
    }

    fun log(msg: String) {
        runOnUiThread {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logText.append("[$time] $msg\n")
            logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (VoiceService.isRunning) {
            statusText.text = "DİNLİYORUM"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.cyan))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermissionsAndStart()
            } else {
                log("Mikrofon izni reddedildi")
            }
        }
    }
}