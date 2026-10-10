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
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

class CommandProcessor(
    private val context: Context,
    private val tts: TtsManager
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun process(rawInput: String, onDone: () -> Unit) {
        val input = normalize(rawInput)

        val acc = JarvisAccessibilityService.instance

        // ========== ZAMAN & TARİH ==========
        if (matches(input, "saat", "kaç", "ne") || input == "saat") {
            val cal = Calendar.getInstance()
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val m = cal.get(Calendar.MINUTE)
            val mStr = if (m < 10) "0$m" else "$m"
            tts.speak("Saat şu an $h:$mStr", onDone)
            return
        }

        if (matches(input, "tarih", "bugün") || input.contains("hangi gün") || input.contains("kaçıncı")) {
            val sdf = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr", "TR"))
            tts.speak("Bugün ${sdf.format(Date())}", onDone)
            return
        }

        // ========== PİL ==========
        if (matches(input, "pil", "batarya", "şarj", "güç")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val status = when {
                level > 80 -> "Pil durumu çok iyi"
                level > 40 -> "Pil durumu normal"
                level > 20 -> "Pil düşük, şarj etmeyi düşün"
                else -> "Pil kritik seviyede, hemen şarj et"
            }
            tts.speak("Pil seviyesi yüzde $level. $status", onDone)
            return
        }

        // ========== SİSTEM KONTROLLERİ ==========
        if (matches(input, "ana ekran", "home", "ev")) {
            acc?.pressHome()
            tts.speak("Ana ekrana dönüyorum", onDone)
            return
        }

        if (matches(input, "geri") && (input.contains("bas") || input.contains("git") || input.contains("dön"))) {
            acc?.pressBack()
            tts.speak("Geri", onDone)
            return
        }

        if (matches(input, "son uygulamalar", "recents", "geçmiş") || (input.contains("son") && input.contains("uygulama"))) {
            acc?.openRecents()
            tts.speak("Son uygulamalar açılıyor", onDone)
            return
        }

        if (matches(input, "bildirim") && (input.contains("aç") || input.contains("göster") || input.contains("panel"))) {
            acc?.openNotifications()
            tts.speak("Bildirimler açılıyor", onDone)
            return
        }

        if (matches(input, "kilit", "kilitle") || input.contains("ekranı kilitle")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                acc?.lockScreen()
            }
            tts.speak("Ekran kilitleniyor", onDone)
            return
        }

        // ========== KAYDIRMA ==========
        if (matches(input, "aşağı kaydır", "aşağı git", "aşağı")) {
            acc?.scrollDown()
            tts.speak("Aşağı kaydırıyorum", onDone)
            return
        }
        if (matches(input, "yukarı kaydır", "yukarı git", "yukarı")) {
            acc?.scrollUp()
            tts.speak("Yukarı kaydırıyorum", onDone)
            return
        }

        // ========== EKRAN OKUMA ==========
        if (matches(input, "ekranda ne", "ekranı oku", "ne yazıyor", "ekranı oku")) {
            val text = acc?.readScreenText() ?: ""
            if (text.isBlank()) tts.speak("Ekranda okunacak bir şey bulamadım", onDone)
            else tts.speak(text.take(350), onDone)
            return
        }

        // ========== SES KONTROLÜ ==========
        if (matches(input, "sesi aç", "sesi yükselt", "ses artır", "daha yüksek")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            tts.speak("Ses yükseltiliyor", onDone)
            return
        }
        if (matches(input, "sesi kıs", "sesi azalt", "ses düşür", "daha kısık")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            tts.speak("Ses kısılıyor", onDone)
            return
        }
        if (matches(input, "sessiz", "mute", "sustur")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            tts.speak("Ses kapatıldı", onDone)
            return
        }
        if (matches(input, "sesi aç") && input.contains("sessiz")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            tts.speak("Ses açıldı", onDone)
            return
        }

        // ========== PARLAKLIK ==========
        if (matches(input, "parlaklık", "ekran parlaklığı")) {
            try {
                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                tts.speak("Parlaklık ayarları açılıyor", onDone)
            } catch (e: Exception) {
                tts.speak("Açamadım", onDone)
            }
            return
        }

        // ========== WIFI / BLUETOOTH / AYARLAR ==========
        if (matches(input, "wifi", "wi-fi", "kablosuz")) {
            openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi ayarları açılıyor", onDone)
            return
        }
        if (matches(input, "bluetooth", "mavi diş")) {
            openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth ayarları açılıyor", onDone)
            return
        }
        if (matches(input, "ayarlar") && (input.contains("aç") || input.contains("git"))) {
            openApp("com.android.settings")
            tts.speak("Ayarlar açılıyor", onDone)
            return
        }
        if (matches(input, "konum", "gps", "lokasyon")) {
            openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Konum ayarları açılıyor", onDone)
            return
        }
        if (matches(input, "uçak modu", "airplane")) {
            openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Uçak modu ayarları açılıyor", onDone)
            return
        }
        if (matches(input, "pil ayarları", "batarya ayar")) {
            openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Pil ayarları açılıyor", onDone)
            return
        }
        if (matches(input, "uygulama ayarları", "uygulamalar")) {
            openSettings(Settings.ACTION_APPLICATION_SETTINGS, "Uygulama listesi açılıyor", onDone)
            return
        }

        // ========== KAMERA & GALERİ ==========
        if (matches(input, "kamera", "fotoğraf çek", "çek")) {
            try {
                val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                tts.speak("Kamera açılıyor", onDone)
            } catch (e: Exception) {
                tts.speak("Kamera açılamadı", onDone)
            }
            return
        }
        if (matches(input, "galeri", "fotoğraflar", "resimler")) {
            try {
                val i = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                tts.speak("Galeri açılıyor", onDone)
            } catch (e: Exception) {
                tts.speak("Galeri açılamadı", onDone)
            }
            return
        }

        // ========== YOUTUBE ==========
        if (matches(input, "youtube", "youtub")) {
            val query = input
                .replace("youtube", "").replace("youtub", "")
                .replace("dan", "").replace("den", "").replace("da", "").replace("de", "")
                .replace("aç", "").replace("açar", "").replace("ara", "").replace("bul", "")
                .replace("oynat", "").replace("izle", "")
                .replace("\\s+".toRegex(), " ").trim()
                .replace(" ", "+")

            if (query.isBlank()) {
                openUrl("https://m.youtube.com", "YouTube açılıyor", onDone)
            } else {
                openUrl("https://m.youtube.com/results?search_query=$query", "YouTube'da aranıyor", onDone)
            }
            return
        }

        // ========== MÜZİK & SPOTIFY ==========
        if (matches(input, "spotify", "müzik aç")) {
            if (openApp("com.spotify.music")) tts.speak("Spotify açılıyor", onDone)
            else tts.speak("Spotify yüklü değil", onDone)
            return
        }
        if (matches(input, "müzik") && (input.contains("çal") || input.contains("oynat") || input.contains("başlat"))) {
            if (openApp("com.spotify.music") || openApp("com.google.android.apps.youtube.music")) {
                tts.speak("Müzik uygulaması açılıyor", onDone)
            } else tts.speak("Müzik uygulaması bulunamadı", onDone)
            return
        }

        // ========== SOSYAL ==========
        if (matches(input, "whatsapp", "whats uygulama", "whatsapp aç")) {
            if (openApp("com.whatsapp")) tts.speak("WhatsApp açılıyor", onDone)
            else tts.speak("WhatsApp yüklü değil", onDone)
            return
        }
        if (matches(input, "instagram", "insta")) {
            if (openApp("com.instagram.android")) tts.speak("Instagram açılıyor", onDone)
            else tts.speak("Instagram yüklü değil", onDone)
            return
        }
        if (matches(input, "twitter", "x uygulaması", "x aç")) {
            if (openApp("com.twitter.android")) tts.speak("X açılıyor", onDone)
            else tts.speak("X yüklü değil", onDone)
            return
        }
        if (matches(input, "telegram")) {
            if (openApp("org.telegram.messenger")) tts.speak("Telegram açılıyor", onDone)
            else tts.speak("Telegram yüklü değil", onDone)
            return
        }
        if (matches(input, "facebook", "face")) {
            if (openApp("com.facebook.katana")) tts.speak("Facebook açılıyor", onDone)
            else tts.speak("Facebook yüklü değil", onDone)
            return
        }
        if (matches(input, "tiktok")) {
            if (openApp("com.zhiliaoapp.musically")) tts.speak("TikTok açılıyor", onDone)
            else tts.speak("TikTok yüklü değil", onDone)
            return
        }
        if (matches(input, "discord")) {
            if (openApp("com.discord")) tts.speak("Discord açılıyor", onDone)
            else tts.speak("Discord yüklü değil", onDone)
            return
        }

        // ========== TARAYICI & ARAMA ==========
        if (matches(input, "chrome", "tarayıcı", "browser")) {
            if (openApp("com.android.chrome")) tts.speak("Chrome açılıyor", onDone)
            else openUrl("https://www.google.com", "Tarayıcı açılıyor", onDone)
            return
        }

        if (matches(input, "google") && (input.contains("ara") || input.contains("bul") || input.contains("search"))) {
            val q = input.replace("google", "").replace("da", "").replace("de", "")
                .replace("ara", "").replace("bul", "").replace("search", "").trim()
            if (q.isNotBlank()) {
                openUrl("https://www.google.com/search?q=${Uri.encode(q)}", "$q aranıyor", onDone)
            } else {
                tts.speak("Ne aramamı istersin?", onDone)
            }
            return
        }

        if (matches(input, "ara") && !input.contains("youtube") && !input.contains("google")) {
            val q = input.replace("ara", "").replace("bul", "").trim()
            if (q.isNotBlank()) {
                openUrl("https://www.google.com/search?q=${Uri.encode(q)}", "$q aranıyor", onDone)
            } else tts.speak("Ne aramamı istersin?", onDone)
            return
        }

        // ========== HARİTA ==========
        if (matches(input, "harita", "maps", "yol tarifi", "nerede")) {
            val query = input.replace("harita", "").replace("maps", "")
                .replace("yol tarifi", "").replace("nerede", "").replace("aç", "").trim()
            if (query.isBlank()) {
                openUrl("https://maps.google.com", "Haritalar açılıyor", onDone)
            } else {
                openUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}", "Haritada aranıyor", onDone)
            }
            return
        }

        // ========== ALARM & ZAMANLAYICI ==========
        if (matches(input, "alarm kur", "alarm ayarla", "alarm")) {
            try {
                val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                tts.speak("Alarm uygulaması açılıyor", onDone)
            } catch (e: Exception) {
                tts.speak("Alarm açılamadı", onDone)
            }
            return
        }
        if (matches(input, "zamanlayıcı", "timer", "sayaç")) {
            try {
                val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                tts.speak("Zamanlayıcı açılıyor", onDone)
            } catch (e: Exception) {
                tts.speak("Zamanlayıcı açılamadı", onDone)
            }
            return
        }

        // ========== TAKVİM ==========
        if (matches(input, "takvim", "calendar", "ajanda", "randevu")) {
            try {
                val i = Intent(Intent.ACTION_VIEW).apply {
                    data = CalendarContract.CONTENT_URI
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                tts.speak("Takvim açılıyor", onDone)
            } catch (e: Exception) {
                openApp("com.google.android.calendar")
                tts.speak("Takvim açılıyor", onDone)
            }
            return
        }

        // ========== TELEFON ==========
        if (matches(input, "ara") && (input.contains("numara") || input.contains("telefon") || input.matches(Regex(".*\\d{3,}.*")))) {
            val number = input.replace(Regex("[^0-9+]"), "")
            if (number.length >= 7) {
                try {
                    val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(i)
                    tts.speak("Arama ekranı açılıyor", onDone)
                } catch (e: Exception) {
                    tts.speak("Arama yapılamadı", onDone)
                }
            } else {
                tts.speak("Numarayı net söyleyebilir misin?", onDone)
            }
            return
        }
        if (matches(input, "rehber", "kişiler", "kontaklar")) {
            try {
                val i = Intent(Intent.ACTION_VIEW).apply {
                    type = "vnd.android.cursor.dir/contact"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                tts.speak("Rehber açılıyor", onDone)
            } catch (e: Exception) {
                tts.speak("Rehber açılamadı", onDone)
            }
            return
        }

        // ========== MESAJ ==========
        if (matches(input, "mesaj", "sms", "yazı yaz")) {
            try {
                val i = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                tts.speak("Mesaj uygulaması açılıyor", onDone)
            } catch (e: Exception) {
                tts.speak("Mesaj açılamadı", onDone)
            }
            return
        }

        // ========== HESAP MAKİNESİ ==========
        if (matches(input, "hesap makinesi", "hesapla", "calculator", "matematik")) {
            val calcPackages = listOf(
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.sec.android.app.popupcalculator",
                "com.miui.calculator"
            )
            var opened = false
            for (pkg in calcPackages) {
                if (openApp(pkg)) {
                    opened = true
                    break
                }
            }
            if (opened) tts.speak("Hesap makinesi açılıyor", onDone)
            else tts.speak("Hesap makinesi bulunamadı", onDone)
            return
        }

        // ========== NOTLAR ==========
        if (matches(input, "not al", "not yaz", "notlar", "not defteri")) {
            val notePackages = listOf(
                "com.google.android.keep",
                "com.samsung.android.app.notes",
                "com.miui.notes",
                "com.simplemobiletools.notes.pro"
            )
            var opened = false
            for (pkg in notePackages) {
                if (openApp(pkg)) {
                    opened = true
                    break
                }
            }
            if (opened) tts.speak("Notlar açılıyor", onDone)
            else tts.speak("Not uygulaması bulunamadı", onDone)
            return
        }

        // ========== ÇEVİRİ ==========
        if (matches(input, "çevir", "translate", "tercüme")) {
            openUrl("https://translate.google.com", "Çeviri açılıyor", onDone)
            return
        }

        // ========== HAVA DURUMU ==========
        if (matches(input, "hava", "hava durumu", "yağmur", "sıcaklık")) {
            openUrl("https://www.google.com/search?q=hava+durumu", "Hava durumu kontrol ediliyor", onDone)
            return
        }

        // ========== OYUNLAR & EĞLENCE ==========
        if (matches(input, "oyun", "game") && input.contains("aç")) {
            tts.speak("Hangi oyunu açmamı istersin? Uygulama adını söyle", onDone)
            return
        }

        // ========== GENEL UYGULAMA AÇMA ==========
        // "X aç" veya "X uygulamasını aç"
        val openMatch = Regex("(.+?)(?:\\s+uygulamasını)?\\s+aç(?:ar)?$").find(input)
        if (openMatch != null) {
            val appName = openMatch.groupValues[1].trim()
            if (appName.isNotBlank() && appName.length > 2) {
                // Try common mappings
                val common = mapOf(
                    "youtube" to "com.google.android.youtube",
                    "maps" to "com.google.android.apps.maps",
                    "gmail" to "com.google.android.gm",
                    "drive" to "com.google.android.apps.docs",
                    "photos" to "com.google.android.apps.photos",
                    "play store" to "com.android.vending",
                    "store" to "com.android.vending",
                    "play" to "com.android.vending",
                    "netflix" to "com.netflix.mediaclient",
                    "twitch" to "tv.twitch.android.app",
                    "reddit" to "com.reddit.frontpage",
                    "pinterest" to "com.pinterest",
                    "linkedin" to "com.linkedin.android",
                    "snapchat" to "com.snapchat.android",
                    "skype" to "com.skype.raider",
                    "zoom" to "us.zoom.videomeetings",
                    "teams" to "com.microsoft.teams",
                    "outlook" to "com.microsoft.office.outlook",
                    "word" to "com.microsoft.office.word",
                    "excel" to "com.microsoft.office.excel",
                    "powerpoint" to "com.microsoft.office.powerpoint",
                    "files" to "com.google.android.apps.nbu.files",
                    "dosya" to "com.google.android.apps.nbu.files",
                    "clock" to "com.google.android.deskclock",
                    "saat" to "com.google.android.deskclock",
                    "contacts" to "com.google.android.contacts"
                )
                val pkg = common[appName]
                if (pkg != null && openApp(pkg)) {
                    tts.speak("${appName.replaceFirstChar { it.uppercase() }} açılıyor", onDone)
                    return
                }
                // Fallback: search Play Store
                openUrl("https://play.google.com/store/search?q=${Uri.encode(appName)}", "$appName aranıyor", onDone)
                return
            }
        }

        // ========== SELAMLAŞMA & SOHBET ==========
        if (matches(input, "merhaba", "selam", "hey", "günaydın", "iyi akşamlar", "iyi geceler")) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when {
                hour < 12 -> "Günaydın"
                hour < 18 -> "Merhaba"
                else -> "İyi akşamlar"
            }
            tts.speak("$greeting efendim, size nasıl yardımcı olabilirim?", onDone)
            return
        }

        if (matches(input, "nasılsın", "naber", "ne haber", "nasıl gidiyor")) {
            val responses = listOf(
                "Tüm sistemlerim çalışıyor efendim",
                "Harika hissediyorum, emrinize amadeyim",
                "Sistemler stabil, ne istersiniz?",
                "Her şey yolunda, size nasıl yardımcı olayım?"
            )
            tts.speak(responses.random(), onDone)
            return
        }

        if (matches(input, "teşekkür", "sağol", "sağ ol", "eyvallah", "süper", "harika")) {
            val responses = listOf(
                "Rica ederim efendim",
                "Her zaman",
                "Buyurun, başka bir şey?",
                "Memnuniyetle"
            )
            tts.speak(responses.random(), onDone)
            return
        }

        if (matches(input, "kimsin", "sen kimsin", "adın ne")) {
            tts.speak("Ben Jarvis, senin kişisel sesli asistanınım. Türkçe konuşuyorum ve telefonunu kontrol edebiliyorum.", onDone)
            return
        }

        if (matches(input, "ne yapabilirsin", "yeteneklerin", "yardım", "komutlar", "ne biliyorsun")) {
            tts.speak(
                "Saat, tarih, pil durumu söyleyebilirim. " +
                "Uygulama açabilir, YouTube ve Google'da arama yapabilirim. " +
                "Wi-Fi, Bluetooth, ses ve parlaklık ayarlarını yönetebilirim. " +
                "Ana ekrana gidebilir, geri basabilir, ekranı okuyabilirim. " +
                "Alarm, takvim, harita, kamera ve daha birçok şeyi açabilirim. " +
                "Ne istersin?",
                onDone
            )
            return
        }

        // ========== ŞAKA ==========
        if (matches(input, "şaka", "espri", "güldür", "komik")) {
            val jokes = listOf(
                "Programcının eşi markete git der. Programcı der ki: fonksiyon çağrısı yap, ben bekliyorum.",
                "Neden programcılar karanlıkta çalışır? Çünkü ışık bug üretir.",
                "İki byte bir barda oturuyor. Biri diğerine: yüzüm tanıdık geliyor mu?",
                "Bir SQL sorgusu bara girer. İki masa görür: bunlar JOIN mi?",
                "Neden bilgisayarlar soğuk algınlığına yakalanmaz? Çünkü pencereleri her zaman kapalıdır.",
                "Yazılımcı neden denize girmez? Çünkü infinite loop'a girer.",
                "Bir bug bara girer. Barmen der: sen burada ne arıyorsun? Bug: ben sadece test ediyorum."
            )
            tts.speak(jokes.random(), onDone)
            return
        }

        // ========== KAPAT / ÇIK ==========
        if (matches(input, "kapat", "çık", "güle güle", "hoşça kal", "bye", "görüşürüz")) {
            tts.speak("Görüşürüz efendim, her zaman buradayım", onDone)
            return
        }

        // ========== ANLAMADIM ==========
        val fallbacks = listOf(
            "Anlayamadım efendim, tekrar söyler misiniz?",
            "Bu komutu tanıyamadım. Yardım dersen yeteneklerimi anlatırım.",
            "Tam anlayamadım. Daha net söyler misin?",
            "Bu emri çözümleyemedim. Başka bir şekilde dener misin?"
        )
        tts.speak(fallbacks.random(), onDone)
    }

    // ========== YARDIMCI FONKSİYONLAR ==========

    private fun normalize(raw: String): String {
        return raw.lowercase(Locale("tr", "TR"))
            .replace("jarvis", "")
            .replace("carvis", "")
            .replace("jarviş", "")
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
        } catch (e: Exception) {
            false
        }
    }

    private fun openUrl(url: String, speakText: String, onDone: () -> Unit) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            tts.speak(speakText, onDone)
        } catch (e: Exception) {
            tts.speak("Açamadım", onDone)
        }
    }

    private fun openSettings(action: String, speakText: String, onDone: () -> Unit) {
        try {
            context.startActivity(Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            tts.speak(speakText, onDone)
        } catch (e: Exception) {
            tts.speak("Açamadım", onDone)
        }
    }
}
