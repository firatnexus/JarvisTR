<div align="center">

# ⚡ JARVIS TR

### Türkçe Sesli Asistan • Iron Man Edition

<br>

![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Material](https://img.shields.io/badge/Material%203-Dark-00E5FF?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)

<br>

**“Emrinize amadeyim, efendim.”**

<br>

</div>

---

## 🚀 Nedir?

**JarvisTR**, tamamen Türkçe konuşan, telefonunu kontrol edebilen modern bir sesli asistan.  
Iron Man tarzı koyu tema, animasyonlu Arc Reactor ve 50+ güçlü komutla gelir.

Sesle uygulama aç, YouTube’da ara, ses seviyesini ayarla, ekranı oku, alarm kur…  
Android’in izin verdiği her şeyi sesinle yap.

---

## ✨ Özellikler

| Kategori | Yetenekler |
|----------|------------|
| 🕐 **Zaman & Sistem** | Saat, tarih, pil durumu, ana ekran, geri, son uygulamalar, kilit |
| 🔊 **Ses & Ekran** | Sesi yükselt / kıs / sessiz, parlaklık ayarları, ekran okuma |
| 📱 **Uygulamalar** | WhatsApp, Instagram, YouTube, Spotify, Chrome, Telegram, TikTok… |
| 🔍 **Arama** | YouTube arama, Google arama, Harita, Play Store |
| 🛠️ **Araçlar** | Alarm, zamanlayıcı, takvim, kamera, galeri, hesap makinesi, notlar |
| 💬 **Sohbet** | Selamlaşma, şaka, yardım, doğal Türkçe yanıtlar |

---

## 🖼️ Ekran Görünümü

```
┌─────────────────────────────────────┐
│           J A R V I S               │
│         TR • Akıllı Asistan         │
│                                     │
│            ⚡ Arc Reactor            │
│                                     │
│   ┌─────────────────────────────┐   │
│   │  KOMUT GEÇMİŞİ              │   │
│   │  [22:15] Dinleme başladı    │   │
│   │  [22:15] Jarvis YouTube aç  │   │
│   └─────────────────────────────┘   │
│                                     │
│   [ BAŞLAT ]  [ DURDUR ]  [ AYAR ]  │
└─────────────────────────────────────┘
```

---

## 🎤 Örnek Komutlar

```bash
"Jarvis saat kaç"
"Jarvis pil ne kadar"
"Jarvis YouTube korku filmleri ara"
"Jarvis sesi yükselt"
"Jarvis WhatsApp aç"
"Jarvis ana ekran"
"Jarvis harita İstanbul"
"Jarvis alarm kur"
"Jarvis şaka anlat"
"Jarvis ne yapabilirsin"
```

---

## 📦 Kurulum

1. Projeyi klonla veya indir
2. Android Studio ile aç → **Sync Project**
3. Telefona yükle
4. **Erişilebilirlik** iznini ver (`JarvisTR Kontrolü`)
5. Mikrofon iznini ver
6. **BAŞLAT** butonuna bas

```bash
git clone https://github.com/KULLANICI_ADIN/JarvisTR.git
```

---

## 🛠️ Teknik Detaylar

- **Dil:** Kotlin
- **Min SDK:** 26 (Android 8.0)
- **UI:** Material 3 Dark + özel Arc Reactor
- **Ses:** SpeechRecognizer (tr-TR) + TextToSpeech
- **Kontrol:** AccessibilityService
- **Servis:** Foreground Service (mikrofon)

---

## 📁 Proje Yapısı

```
JarvisTR/
├── app/
│   └── src/main/
│       ├── java/com/jarvis/tr/
│       │   ├── CommandProcessor.kt      # 50+ komut
│       │   ├── MainActivity.kt          # UI & animasyon
│       │   ├── VoiceService.kt          # Sürekli dinleme
│       │   ├── TtsManager.kt            # Konuşma
│       │   ├── PrefsManager.kt          # Ayarlar
│       │   └── JarvisAccessibilityService.kt
│       └── res/
│           ├── layout/activity_main.xml
│           ├── drawable/                # Reactor & UI
│           └── values/                  # Tema & renkler
├── README.md
└── ...
```

---

## ⚠️ Önemli Notlar

- Bazı sistem ayarları (uçak modu aç/kapa gibi) Android güvenlik politikası nedeniyle sadece ayar sayfasını açar.
- Erişilebilirlik izni olmadan telefon kontrolü çalışmaz.
- Sürekli dinleme için uygulama arka planda açık kalmalıdır.

---

## 📜 Lisans

MIT License — Özgürce kullan, geliştir, paylaş.

---

<div align="center">

**Yapıldı ❤️ ile Türkiye’de**

`v2.0` • Material 3 • 50+ Komut

</div>
