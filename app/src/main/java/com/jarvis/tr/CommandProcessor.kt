package com.jarvis.tr

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*

class CommandProcessor(
    private val context: Context,
    private val tts: TtsManager
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = PrefsManager(context)
    private val aiResolver = AiIntentResolver(prefs)

    fun process(rawInput: String, onDone: () -> Unit) {
        val input = normalize(rawInput)

        // 1. Önce AI ile anlamaya çalış
        if (prefs.aiEnabled && prefs.groqApiKey.isNotBlank()) {
            aiResolver.resolve(rawInput) { result ->
                if (result != null && result.intent != "unknown") {
                    executeAiIntent(result, onDone)
                } else {
                    // AI başarısız → kural tabanlıya düş
                    processRules(input, onDone)
                }
            }
            return
        }

        // AI kapalıysa direkt kurallar
        processRules(input, onDone)
    }

    // ==================== AI INTENT ÇALIŞTIRICI ====================
    private fun executeAiIntent(result: AiIntentResolver.IntentResult, onDone: () -> Unit) {
        val acc = JarvisAccessibilityService.instance
        val p = result.params

        when (result.intent) {
            "get_time" -> {
                val cal = Calendar.getInstance()
                val h = cal.get(Calendar.HOUR_OF_DAY)
                val m = cal.get(Calendar.MINUTE)
                val mStr = if (m < 10) "0$m" else "$m"
                tts.speak("Saat şu an $h:$mStr", onDone)
            }
            "get_date" -> {
                val sdf = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr", "TR"))
                tts.speak("Bugün ${sdf.format(Date())}", onDone)
            }
            "get_battery" -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                tts.speak("Pil seviyesi yüzde $level", onDone)
            }
            "open_home" -> {
                acc?.pressHome()
                tts.speak("Ana ekrana dönüyorum", onDone)
            }
            "press_back" -> {
                acc?.pressBack()
                tts.speak("Geri", onDone)
            }
            "open_notifications" -> {
                acc?.openNotifications()
                tts.speak("Bildirimler açılıyor", onDone)
            }
            "open_recents" -> {
                acc?.openRecents()
                tts.speak("Son uygulamalar", onDone)
            }
            "lock_screen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) acc?.lockScreen()
                tts.speak("Ekran kilitleniyor", onDone)
            }
            "scroll_down" -> {
                acc?.scrollDown()
                tts.speak("Aşağı kaydırıyorum", onDone)
            }
            "scroll_up" -> {
                acc?.scrollUp()
                tts.speak("Yukarı kaydırıyorum", onDone)
            }
            "read_screen" -> {
                val text = acc?.readScreenText() ?: ""
                if (text.isBlank()) tts.speak("Ekranda okunacak bir şey bulamadım", onDone)
                else tts.speak(text.take(350), onDone)
            }
            "volume_up" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                tts.speak("Ses yükseltiliyor", onDone)
            }
            "volume_down" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                tts.speak("Ses kısılıyor", onDone)
            }
            "volume_mute" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                tts.speak("Ses kapatıldı", onDone)
            }
            "open_wifi" -> openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi ayarları açılıyor", onDone)
            "open_bluetooth" -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth ayarları açılıyor", onDone)
            "open_settings" -> {
                openApp("com.android.settings")
                tts.speak("Ayarlar açılıyor", onDone)
            }
            "open_camera" -> {
                try {
                    context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    tts.speak("Kamera açılıyor", onDone)
                } catch (e: Exception) { tts.speak("Kamera açılamadı", onDone) }
            }
            "open_gallery" -> {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        type = "image/*"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    tts.speak("Galeri açılıyor", onDone)
                } catch (e: Exception) { tts.speak("Galeri açılamadı", onDone) }
            }
            "open_youtube" -> {
                val q = p["query"]?.replace(" ", "+") ?: ""
                if (q.isBlank()) openUrl("https://m.youtube.com", "YouTube açılıyor", onDone)
                else openUrl("https://m.youtube.com/results?search_query=$q", "YouTube'da aranıyor", onDone)
            }
            "open_spotify" -> {
                if (openApp("com.spotify.music")) tts.speak("Spotify açılıyor", onDone)
                else tts.speak("Spotify yüklü değil", onDone)
            }
            "open_whatsapp" -> {
                if (openApp("com.whatsapp")) tts.speak("WhatsApp açılıyor", onDone)
                else tts.speak("WhatsApp yüklü değil", onDone)
            }
            "open_instagram" -> {
                if (openApp("com.instagram.android")) tts.speak("Instagram açılıyor", onDone)
                else tts.speak("Instagram yüklü değil", onDone)
            }
            "open_telegram" -> {
                if (openApp("org.telegram.messenger")) tts.speak("Telegram açılıyor", onDone)
                else tts.speak("Telegram yüklü değil", onDone)
            }
            "open_chrome" -> {
                if (openApp("com.android.chrome")) tts.speak("Chrome açılıyor", onDone)
                else openUrl("https://www.google.com", "Tarayıcı açılıyor", onDone)
            }
            "search_google" -> {
                val q = p["query"] ?: ""
                if (q.isNotBlank()) openUrl("https://www.google.com/search?q=${Uri.encode(q)}", "$q aranıyor", onDone)
                else tts.speak("Ne aramamı istersin?", onDone)
            }
            "open_maps" -> {
                val q = p["query"] ?: ""
                if (q.isBlank()) openUrl("https://maps.google.com", "Haritalar açılıyor", onDone)
                else openUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(q)}", "Haritada aranıyor", onDone)
            }
            "set_alarm" -> {
                try {
                    context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    tts.speak("Alarm açılıyor", onDone)
                } catch (e: Exception) { tts.speak("Alarm açılamadı", onDone) }
            }
            "set_timer" -> {
                try {
                    context.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    tts.speak("Zamanlayıcı açılıyor", onDone)
                } catch (e: Exception) { tts.speak("Zamanlayıcı açılamadı", onDone) }
            }
            "open_calendar" -> {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        data = CalendarContract.CONTENT_URI
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    tts.speak("Takvim açılıyor", onDone)
                } catch (e: Exception) {
                    openApp("com.google.android.calendar")
                    tts.speak("Takvim açılıyor", onDone)
                }
            }
            "open_calculator" -> {
                val pkgs = listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator")
                if (pkgs.any { openApp(it) }) tts.speak("Hesap makinesi açılıyor", onDone)
                else tts.speak("Hesap makinesi bulunamadı", onDone)
            }
            "open_notes" -> {
                val pkgs = listOf("com.google.android.keep", "com.samsung.android.app.notes", "com.miui.notes")
                if (pkgs.any { openApp(it) }) tts.speak("Notlar açılıyor", onDone)
                else tts.speak("Not uygulaması bulunamadı", onDone)
            }
            "open_translate" -> openUrl("https://translate.google.com", "Çeviri açılıyor", onDone)
            "get_weather" -> openUrl("https://www.google.com/search?q=hava+durumu", "Hava durumu kontrol ediliyor", onDone)
            "open_app" -> {
                val name = p["name"] ?: ""
                if (name.isNotBlank()) {
                    // Basit eşleme
                    val common = mapOf(
                        "youtube" to "com.google.android.youtube",
                        "maps" to "com.google.android.apps.maps",
                        "gmail" to "com.google.android.gm",
                        "netflix" to "com.netflix.mediaclient",
                        "discord" to "com.discord"
                    )
                    val pkg = common[name.lowercase()]
                    if (pkg != null && openApp(pkg)) tts.speak("$name açılıyor", onDone)
                    else openUrl("https://play.google.com/store/search?q=${Uri.encode(name)}", "$name aranıyor", onDone)
                } else tts.speak("Hangi uygulamayı açayım?", onDone)
            }
            "tell_joke" -> {
                val jokes = listOf(
                    "Programcının eşi markete git der. Programcı der ki: fonksiyon çağrısı yap, ben bekliyorum.",
                    "Neden programcılar karanlıkta çalışır? Çünkü ışık bug üretir.",
                    "İki byte bir barda oturuyor. Biri diğerine: yüzüm tanıdık geliyor mu?",
                    "Bir SQL sorgusu bara girer. İki masa görür: bunlar JOIN mi?"
                )
                tts.speak(jokes.random(), onDone)
            }
            "greeting" -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val g = when {
                    hour < 12 -> "Günaydın"
                    hour < 18 -> "Merhaba"
                    else -> "İyi akşamlar"
                }
                tts.speak("$g efendim, size nasıl yardımcı olabilirim?", onDone)
            }
            "how_are_you" -> tts.speak("Tüm sistemlerim çalışıyor efendim", onDone)
            "thanks" -> tts.speak("Rica ederim efendim", onDone)
            "help" -> tts.speak("Uygulama açabilir, arama yapabilir, ses ayarlayabilir, ekranı okuyabilirim. Ne istersin?", onDone)
            "chat" -> {
                val reply = result.reply ?: p["reply"] ?: "Anladım efendim."
                tts.speak(reply, onDone)
            }
            else -> {
                // Bilinmeyen → kural tabanlıya düş
                processRules(normalize(result.intent), onDone)
            }
        }
    }

    // ==================== KURAL TABANLI (FALLBACK) ====================
    private fun processRules(input: String, onDone: () -> Unit) {
        val acc = JarvisAccessibilityService.instance

        if (matches(input, "saat", "kaç", "ne") || input == "saat") {
            val cal = Calendar.getInstance()
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val m = cal.get(Calendar.MINUTE)
            val mStr = if (m < 10) "0$m" else "$m"
            tts.speak("Saat şu an $h:$mStr", onDone)
            return
        }

        if (matches(input, "tarih", "bugün") || input.contains("hangi gün")) {
            val sdf = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr", "TR"))
            tts.speak("Bugün ${sdf.format(Date())}", onDone)
            return
        }

        if (matches(input, "pil", "batarya", "şarj")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            tts.speak("Pil seviyesi yüzde $level", onDone)
            return
        }

        if (matches(input, "ana ekran", "home")) {
            acc?.pressHome()
            tts.speak("Ana ekrana dönüyorum", onDone)
            return
        }

        if (matches(input, "geri") && (input.contains("bas") || input.contains("git"))) {
            acc?.pressBack()
            tts.speak("Geri", onDone)
            return
        }

        if (matches(input, "bildirim") && (input.contains("aç") || input.contains("göster"))) {
            acc?.openNotifications()
            tts.speak("Bildirimler açılıyor", onDone)
            return
        }

        if (matches(input, "kilit", "kilitle") || input.contains("ekranı kilitle")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) acc?.lockScreen()
            tts.speak("Ekran kilitleniyor", onDone)
            return
        }

        if (matches(input, "aşağı kaydır", "aşağı")) {
            acc?.scrollDown()
            tts.speak("Aşağı kaydırıyorum", onDone)
            return
        }
        if (matches(input, "yukarı kaydır", "yukarı")) {
            acc?.scrollUp()
            tts.speak("Yukarı kaydırıyorum", onDone)
            return
        }

        if (matches(input, "ekranda ne", "ekranı oku")) {
            val text = acc?.readScreenText() ?: ""
            if (text.isBlank()) tts.speak("Ekranda okunacak bir şey bulamadım", onDone)
            else tts.speak(text.take(350), onDone)
            return
        }

        if (matches(input, "sesi aç", "sesi yükselt", "ses artır")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            tts.speak("Ses yükseltiliyor", onDone)
            return
        }
        if (matches(input, "sesi kıs", "sesi azalt", "ses düşür")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            tts.speak("Ses kısılıyor", onDone)
            return
        }
        if (matches(input, "sessiz", "mute")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            tts.speak("Ses kapatıldı", onDone)
            return
        }

        if (matches(input, "wifi", "wi-fi")) {
            openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi ayarları açılıyor", onDone)
            return
        }
        if (matches(input, "bluetooth")) {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth ayarları açılıyor", onDone)
            return
        }
        if (matches(input, "ayarlar") && input.contains("aç")) {
            openApp("com.android.settings")
            tts.speak("Ayarlar açılıyor", onDone)
            return
        }

        if (matches(input, "kamera")) {
            try {
                context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                tts.speak("Kamera açılıyor", onDone)
            } catch (e: Exception) { tts.speak("Kamera açılamadı", onDone) }
            return
        }

        if (matches(input, "youtube")) {
            val query = input.replace("youtube", "").replace("dan", "").replace("den", "")
                .replace("aç", "").replace("ara", "").replace("bul", "").replace("oynat", "")
                .replace("\\s+".toRegex(), " ").trim().replace(" ", "+")
            if (query.isBlank()) openUrl("https://m.youtube.com", "YouTube açılıyor", onDone)
            else openUrl("https://m.youtube.com/results?search_query=$query", "YouTube'da aranıyor", onDone)
            return
        }

        if (matches(input, "whatsapp")) {
            if (openApp("com.whatsapp")) tts.speak("WhatsApp açılıyor", onDone)
            else tts.speak("WhatsApp yüklü değil", onDone)
            return
        }
        if (matches(input, "instagram")) {
            if (openApp("com.instagram.android")) tts.speak("Instagram açılıyor", onDone)
            else tts.speak("Instagram yüklü değil", onDone)
            return
        }
        if (matches(input, "spotify")) {
            if (openApp("com.spotify.music")) tts.speak("Spotify açılıyor", onDone)
            else tts.speak("Spotify yüklü değil", onDone)
            return
        }
        if (matches(input, "chrome", "tarayıcı")) {
            if (openApp("com.android.chrome")) tts.speak("Chrome açılıyor", onDone)
            else tts.speak("Chrome yüklü değil", onDone)
            return
        }

        if (matches(input, "google") && input.contains("ara")) {
            val q = input.replace("google", "").replace("da", "").replace("ara", "").trim()
            if (q.isNotBlank()) openUrl("https://www.google.com/search?q=${Uri.encode(q)}", "$q aranıyor", onDone)
            else tts.speak("Ne aramamı istersin?", onDone)
            return
        }

        if (matches(input, "merhaba", "selam", "hey")) {
            tts.speak("Merhaba efendim, size nasıl yardımcı olabilirim?", onDone)
            return
        }
        if (matches(input, "nasılsın", "naber")) {
            tts.speak("Tüm sistemlerim çalışıyor efendim", onDone)
            return
        }
        if (matches(input, "teşekkür", "sağol", "sağ ol")) {
            tts.speak("Rica ederim efendim", onDone)
            return
        }
        if (matches(input, "şaka")) {
            val jokes = listOf(
                "Programcının eşi markete git der. Programcı der ki: fonksiyon çağrısı yap, ben bekliyorum.",
                "Neden programcılar karanlıkta çalışır? Çünkü ışık bug üretir.",
                "İki byte bir barda oturuyor. Biri diğerine: yüzüm tanıdık geliyor mu?"
            )
            tts.speak(jokes.random(), onDone)
            return
        }
        if (matches(input, "yardım", "ne yapabilirsin")) {
            tts.speak("Uygulama açabilir, YouTube'da arama yapabilir, ekranda gezinip tıklayabilirim. Ne istersiniz?", onDone)
            return
        }

        tts.speak("Anlayamadım efendim, tekrar söyler misiniz?", onDone)
    }

    // ==================== YARDIMCILAR ====================
    private fun normalize(raw: String): String {
        return raw.lowercase(Locale("tr", "TR"))
            .replace("jarvis", "").replace("carvis", "").replace("jarviş", "")
            .replace("[.,!?;:\"']".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun matches(input: String, vararg keywords: String): Boolean {
        return keywords.any { input.contains(it) }
    }

    private fun openApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) { false }
    }

    private fun openUrl(url: String, speakText: String, onDone: () -> Unit) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            tts.speak(speakText, onDone)
        } catch (e: Exception) { tts.speak("Açamadım", onDone) }
    }

    private fun openSettings(action: String, speakText: String, onDone: () -> Unit) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            tts.speak(speakText, onDone)
        } catch (e: Exception) { tts.speak("Açamadım", onDone) }
    }
}
