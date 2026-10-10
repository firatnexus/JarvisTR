# JarvisTR v2.0

Türkçe sesli asistan — Iron Man tarzı, güçlü ve estetik.

## Özellikler

- **50+ sesli komut**: Saat, tarih, pil, sistem kontrolleri, uygulama açma, YouTube/Google arama, ses/parlaklık, Wi-Fi/Bluetooth, kamera, harita, alarm, takvim, mesaj, hesap makinesi, notlar, çeviri, hava durumu ve daha fazlası
- **Erişilebilirlik ile telefon kontrolü**: Ana ekran, geri, kaydırma, ekran okuma, tıklama
- **Modern dark UI**: Arc Reactor animasyonu, glass kartlar, Material 3
- **Doğal Türkçe TTS** + ayarlanabilir pitch/rate
- **Sürekli dinleme** (foreground service)

## Kurulum

1. Android Studio ile projeyi aç
2. Build → Make Project
3. Telefona yükle
4. Uygulamayı aç → **Erişilebilirlik** iznini ver (JarvisTR Kontrolü)
5. Mikrofon iznini ver
6. **BAŞLAT** butonuna bas

## Örnek Komutlar

- "Jarvis saat kaç"
- "Jarvis pil ne kadar"
- "Jarvis YouTube aç"
- "Jarvis YouTube korku filmleri ara"
- "Jarvis sesi yükselt"
- "Jarvis ana ekran"
- "Jarvis WhatsApp aç"
- "Jarvis harita İstanbul"
- "Jarvis alarm kur"
- "Jarvis şaka anlat"
- "Jarvis ne yapabilirsin"

## Teknik

- Kotlin + Material 3
- SpeechRecognizer (tr-TR)
- TextToSpeech
- AccessibilityService
- Foreground Service (mikrofon)

## Sınırlar

Android güvenlik kısıtlamaları nedeniyle bazı sistem ayarları (uçak modu aç/kapa gibi) doğrudan yapılamaz, ayar sayfası açılır.
