package com.jarvis.tr

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * AI katmanı – Groq free tier ile intent anlama.
 * API key yoksa veya hata olursa null döner → kural tabanlı sistem devreye girer.
 */
class AiIntentResolver(private val prefs: PrefsManager) {

    data class IntentResult(
        val intent: String,          // örn: "open_youtube", "get_time", "set_volume"
        val params: Map<String, String> = emptyMap(),
        val reply: String? = null    // AI'nin doğrudan söylemesini istediği metin
    )

    private val executor = Executors.newSingleThreadExecutor()

    fun resolve(userText: String, callback: (IntentResult?) -> Unit) {
        val key = prefs.groqApiKey.trim()
        if (!prefs.aiEnabled || key.isBlank()) {
            callback(null)
            return
        }

        executor.execute {
            try {
                val result = callGroq(key, userText)
                callback(result)
            } catch (e: Exception) {
                Log.e("AiIntent", "AI error: ${e.message}")
                callback(null)
            }
        }
    }

    private fun callGroq(apiKey: String, userText: String): IntentResult? {
        val url = URL("https://api.groq.com/openai/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 12000

        val systemPrompt = """
Sen JarvisTR adında Türkçe sesli asistanın intent sınıflandırıcısısın.
Kullanıcı Türkçe konuşur. Görevin sadece JSON döndürmek.

Mümkün intent'ler:
- get_time
- get_date
- get_battery
- open_home
- press_back
- open_notifications
- open_recents
- lock_screen
- scroll_down
- scroll_up
- read_screen
- volume_up
- volume_down
- volume_mute
- open_wifi
- open_bluetooth
- open_settings
- open_camera
- open_gallery
- open_youtube (param: query)
- open_spotify
- open_whatsapp
- open_instagram
- open_telegram
- open_chrome
- search_google (param: query)
- open_maps (param: query)
- set_alarm
- set_timer
- open_calendar
- open_calculator
- open_notes
- open_translate
- get_weather
- open_app (param: name)
- tell_joke
- greeting
- how_are_you
- thanks
- help
- unknown
- chat (param: reply)  ← eğer yukarıdakilerden hiçbiri değilse, kısa ve doğal Türkçe cevap ver

Sadece şu formatta JSON döndür, başka hiçbir şey yazma:
{"intent":"...", "params":{"query":"..."}, "reply":"..."}

params ve reply opsiyoneldir.
""".trimIndent()

        val body = JSONObject().apply {
            put("model", "llama-3.1-8b-instant")  // hızlı ve ücretsiz
            put("temperature", 0.1)
            put("max_tokens", 150)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userText)
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            Log.e("AiIntent", "HTTP $code")
            return null
        }

        val response = BufferedReader(conn.inputStream.reader()).readText()
        val json = JSONObject(response)
        val content = json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        // JSON'u temizle (bazen ```json ile gelebilir)
        val clean = content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val parsed = JSONObject(clean)
        val intent = parsed.optString("intent", "unknown")
        val reply = parsed.optString("reply", null)

        val params = mutableMapOf<String, String>()
        val paramsObj = parsed.optJSONObject("params")
        if (paramsObj != null) {
            paramsObj.keys().forEach { key ->
                params[key] = paramsObj.optString(key)
            }
        }

        return IntentResult(intent, params, reply)
    }
}
