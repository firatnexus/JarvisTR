package com.jarvis.tr

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var logText: TextView
    private lateinit var statusText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var startBtn: MaterialButton
    private lateinit var stopBtn: MaterialButton
    private lateinit var settingsBtn: MaterialButton
    private lateinit var reactorOuter: View
    private var pulseAnimator: ObjectAnimator? = null

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
        reactorOuter = findViewById(R.id.reactorOuter)

        log("JARVIS TR v2.0 hazır")
        log("Sistemler online • Emrinize amadeyim")
        log("Örnek: \"Jarvis YouTube aç\"")
        log("Örnek: \"Jarvis saat kaç\"")
        log("Örnek: \"Jarvis sesi yükselt\"")

        requestNotifPermission()
        startPulseAnimation(false)

        startBtn.setOnClickListener {
            checkPermissionsAndStart()
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, VoiceService::class.java))
            updateStatus("DURDURULDU", R.color.statusError)
            startPulseAnimation(false)
            log("Servis durduruldu")
            Toast.makeText(this, "JARVIS durdu", Toast.LENGTH_SHORT).show()
        }

        settingsBtn.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun startPulseAnimation(active: Boolean) {
        pulseAnimator?.cancel()
        if (active) {
            pulseAnimator = ObjectAnimator.ofFloat(reactorOuter, "alpha", 0.7f, 1f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                start()
            }
        } else {
            reactorOuter.alpha = 0.85f
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
                .setMessage(
                    "Telefonunu kontrol edebilmem için JarvisTR'yi erişilebilirlik hizmetlerinde açman gerekiyor.\n\n" +
                    "Ayarlar açılacak → JarvisTR Kontrolü'nü bul → AÇ"
                )
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
        updateStatus("DİNLİYORUM", R.color.statusListening)
        startPulseAnimation(true)
        log("Dinleme başladı — konuşabilirsin")
        Toast.makeText(this, "JARVIS dinliyor", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus(text: String, colorRes: Int) {
        statusText.text = text
        statusText.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun showSettingsDialog() {
        val prefs = PrefsManager(this)
        val aiStatus = if (prefs.aiEnabled && prefs.groqApiKey.isNotBlank()) "Açık ✓" else if (prefs.aiEnabled) "Açık (key yok)" else "Kapalı"
        val options = arrayOf(
            "Ses Tonu (Pitch): ${"%.2f".format(prefs.pitch)}",
            "Konuşma Hızı: ${"%.2f".format(prefs.rate)}",
            "Otomatik Yeniden Başlat: ${if (prefs.autoRestart) "Açık" else "Kapalı"}",
            "Kullanıcı Adı: ${prefs.userName.ifBlank { "Ayarlanmamış" }}",
            "AI Asistan: $aiStatus",
            "Groq API Key Gir",
            "Erişilebilirlik Ayarlarını Aç"
        )
        AlertDialog.Builder(this)
            .setTitle("JARVIS Ayarları")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> adjustPitch(prefs)
                    1 -> adjustRate(prefs)
                    2 -> {
                        prefs.autoRestart = !prefs.autoRestart
                        Toast.makeText(this, "Otomatik yeniden başlat: ${if (prefs.autoRestart) "Açık" else "Kapalı"}", Toast.LENGTH_SHORT).show()
                    }
                    3 -> setUserName(prefs)
                    4 -> {
                        prefs.aiEnabled = !prefs.aiEnabled
                        val msg = if (prefs.aiEnabled) "AI açıldı. API key girmeyi unutma." else "AI kapatıldı (sadece kurallar)"
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                    5 -> setApiKey(prefs)
                    6 -> openAccessibilitySettings()
                }
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun setApiKey(prefs: PrefsManager) {
        val input = android.widget.EditText(this).apply {
            setText(prefs.groqApiKey)
            hint = "gsk_..."
            setPadding(48, 32, 48, 32)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Groq API Key")
            .setMessage("Ücretsiz key al: console.groq.com\nLlama 3.1 8B Instant kullanılıyor.")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                prefs.groqApiKey = input.text.toString().trim()
                if (prefs.groqApiKey.isNotBlank()) prefs.aiEnabled = true
                Toast.makeText(this, "API Key kaydedildi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun adjustPitch(prefs: PrefsManager) {
        val values = floatArrayOf(0.6f, 0.75f, 0.85f, 1.0f, 1.15f, 1.3f)
        val labels = values.map { "%.2f".format(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Ses Tonu (Pitch)")
            .setItems(labels) { _, which ->
                prefs.pitch = values[which]
                Toast.makeText(this, "Pitch: ${labels[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun adjustRate(prefs: PrefsManager) {
        val values = floatArrayOf(0.7f, 0.85f, 1.0f, 1.15f, 1.3f)
        val labels = values.map { "%.2f".format(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Konuşma Hızı")
            .setItems(labels) { _, which ->
                prefs.rate = values[which]
                Toast.makeText(this, "Hız: ${labels[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setUserName(prefs: PrefsManager) {
        val input = android.widget.EditText(this).apply {
            setText(prefs.userName)
            hint = "Adınız"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Kullanıcı Adı")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                prefs.userName = input.text.toString().trim()
                Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logText.append("[$time] $msg\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            checkPermissionsAndStart()
        }
    }

    override fun onDestroy() {
        pulseAnimator?.cancel()
        super.onDestroy()
    }
}
