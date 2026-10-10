package com.jarvis.tr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings

class CommandProcessor(
    private val context: Context,
    private val tts: TtsManager
) {

    fun process(rawInput: String, onDone: () -> Unit) {
        val input = rawInput.lowercase().trim()
            .replace("jarvis", "")
            .replace("[.,!?;:]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        val acc = JarvisAccessibilityService.instance

        // SAAT
        if (input.contains("saat") && (input.contains("kaç") || input.contains("ne"))) {
            val cal = java.util.Calendar.getInstance()
            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val m = cal.get(java.util.Calendar.MINUTE)
            tts.speak("Saat şu an $h:$m", onDone)
            return
        }

        // PİL
        if (input.contains("pil") || input.contains("batarya") || input.contains("şarj")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            tts.speak("Pil seviyesi yüzde $level", onDone)
            return
        }

        // ANA EKRAN
        if (input.contains("ana ekran") || input.contains("home")) {
            acc?.pressHome()
            tts.speak("Ana ekrana dönüyorum", onDone)
            return
        }

        // GERİ
        if (input.contains("geri") && (input.contains("bas") || input.contains("git"))) {
            acc?.pressBack()
            tts.speak("Geri", onDone)
            return
        }

        // BİLDİRİMLER
        if (input.contains("bildirim") && (input.contains("aç") || input.contains("göster"))) {
            acc?.openNotifications()
            tts.speak("Bildirimler açılıyor", onDone)
            return
        }

        // KİLİT
        if (input.contains("ekranı kilitle") || (input.contains("kilit") && input.contains("le"))) {
            if (acc != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    acc.lockScreen()
                }
            }
            tts.speak("Ekran kilitleniyor", onDone)
            return
        }

        // KAYDIRMA
        if (input.contains("aşağı kaydır") || (input.contains("aşağı") && input.contains("git"))) {
            acc?.scrollDown()
            tts.speak("Aşağı kaydırıyorum", onDone)
            return
        }
        if (input.contains("yukarı kaydır") || (input.contains("yukarı") && input.contains("git"))) {
            acc?.scrollUp()
            tts.speak("Yukarı kaydırıyorum", onDone)
            return
        }

        // EKRAN OKUMA
        if (input.contains("ekranda ne") || input.contains("ekranı oku")) {
            val text = acc?.readScreenText() ?: ""
            if (text.isBlank()) tts.speak("Ekranda okunacak bir şey bulamadım", onDone)
            else tts.speak(text.take(400), onDone)
            return
        }

        // WIFI
        if (input.contains("wifi") || input.contains("wi-fi")) {
            try {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                tts.speak("Wi-Fi ayarları açılıyor", onDone)
            } catch (e: Exception) {
                tts.speak("Açamadım", onDone)
            }
            return
        }

        // BLUETOOTH
        if (input.contains("bluetooth")) {
            try {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                tts.speak("Bluetooth ayarları açılıyor", onDone)
            } catch (e: Exception) {
                tts.speak("Açamadım", onDone)
            }
            return
        }

        // AYARLAR
        if (input.contains("ayarlar") && input.contains("aç")) {
            openApp("com.android.settings")
            tts.speak("Ayarlar açılıyor", onDone)
            return
        }

        // KAMERA
        if (input.contains("kamera")) {
            try {
                val i = Intent("android.media.action.IMAGE_CAPTURE")
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                tts.speak("Kamera açılıyor", onDone)
            } catch (e: Exception) {
                tts.speak("Kamera açılamadı", onDone)
            }
            return
        }

        // YOUTUBE (arama destekli) — ÖNCE KONTROL EDİLİYOR
        if (input.contains("youtube")) {
            val query = input
                .replace("youtube", "")
                .replace("dan", "").replace("den", "")
                .replace("aç", "").replace("açar", "")
                .replace("ara", "").replace("bul", "")
                .replace("da", "").replace("de", "")
                .replace("oynat", "")
                .replace("\\s+".toRegex(), " ")
                .trim()
                .replace(" ", "+")

            if (query.isBlank()) {
                try {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com"))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                    tts.speak("YouTube açılıyor", onDone)
                } catch (e: Exception) {
                    tts.speak("YouTube açılamadı", onDone)
                }
            } else {
                try {
                    val i = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://m.youtube.com/results?search_query=$query")
                    )
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                    tts.speak("YouTube'da aranıyor", onDone)
                } catch (e: Exception) {
                    tts.speak("Arama yapılamadı", onDone)
                }
            }
            return
        }

        // WHATSAPP
        if (input.contains("whatsapp") || input.contains("whats uygulama")) {
            if (openApp("com.whatsapp")) tts.speak("WhatsApp açılıyor", onDone)
            else tts.speak("WhatsApp yüklü değil", onDone)
            return
        }

        // INSTAGRAM
        if (input.contains("instagram")) {
            if (openApp("com.instagram.android")) tts.speak("Instagram açılıyor", onDone)
            else tts.speak("Instagram yüklü değil", onDone)
            return
        }

        // SPOTIFY
        if (input.contains("spotify")) {
            if (openApp("com.spotify.music")) tts.speak("Spotify açılıyor", onDone)
            else tts.speak("Spotify yüklü değil", onDone)
            return
        }

        // CHROME
        if (input.contains("chrome") || input.contains("tarayıcı")) {
            if (openApp("com.android.chrome")) tts.speak("Chrome açılıyor", onDone)
            else tts.speak("Chrome yüklü değil", onDone)
            return
        }

        // GOOGLE'DA ARA
        if (input.contains("google") && input.contains("ara")) {
            val q = input.replace("google", "").replace("da", "").replace("ara", "").trim()
            if (q.isNotBlank()) {
                val i = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}")
                )
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                tts.speak("$q aranıyor", onDone)
            } else {
                tts.speak("Ne aramamı istersin?", onDone)
            }
            return
        }

        // SELAMLAŞMA
        if (input.startsWith("merhaba") || input.startsWith("selam") || input.startsWith("hey")) {
            tts.speak("Merhaba efendim, size nasıl yardımcı olabilirim?", onDone)
            return
        }

        // NASILSIN
        if (input.contains("nasılsın") || input.contains("naber")) {
            tts.speak("Tüm sistemlerim çalışıyor efendim", onDone)
            return
        }

        // TEŞEKKÜR
        if (input.contains("teşekkür") || input.contains("sağol") || input.contains("sağ ol")) {
            tts.speak("Rica ederim efendim", onDone)
            return
        }

        // ŞAKA
        if (input.contains("şaka")) {
            val jokes = listOf(
                "Programcının eşi markete git der. Programcı der ki: fonksiyon çağrısı yap, ben bekliyorum.",
                "Neden programcılar karanlıkta çalışır? Çünkü ışık bug üretir.",
                "İki byte bir barda oturuyor. Biri diğerine: yüzüm tanıdık geliyor mu?",
                "Bir SQL sorgusu bara girer. İki masa görür: bunlar JOIN mi?"
            )
            tts.speak(jokes.random(), onDone)
            return
        }

        // YARDIM
        if (input.contains("yardım") || input.contains("ne yapabilirsin")) {
            tts.speak("Uygulama açabilir, YouTube'da arama yapabilir, ekranda gezinip tıklayabilirim. Ne istersiniz?", onDone)
            return
        }

        // ANLAMADIM
        tts.speak("Anlayamadım efendim, tekrar söyler misiniz?", onDone)
    }

    private fun openApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}