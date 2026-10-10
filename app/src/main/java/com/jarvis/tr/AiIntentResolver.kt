package com.jarvis.tr

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * AI katmanı – Groq free tier ile intent anlama.
 * Hata olursa null döner → kural tabanlı sistem devreye girer.
 */
class AiIntentResolver(private val prefs: PrefsManager) {

    data class IntentResult(
        val intent: String,
        val params: Map<String, String> = emptyMap(),
        val reply: String? = null
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun resolve(userText: String, callback: (IntentResult?) -> Unit) {
        val key = prefs.groqApiKey.trim()
        if (!prefs.aiEnabled || key.isBlank()) {
            mainHandler.post { callback(null) }
            return
        }

        executor.execute {
            try {
                val result = callGroq(key, userText)
                mainHandler.post { callback(result) }
            } catch (e: Exception) {
                Log.e("AiIntent", "AI error: ${e.message}", e)
                mainHandler.post { callback(null) }
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
        conn.connectTimeout = 10000
        conn.readTimeout = 15000

        val systemPrompt = """
Sen JarvisTR intent sınıflandırıcısısın. Kullanıcı Türkçe konuşur.
SADECE geçerli bir JSON döndür. Başka hiçbir metin, markdown veya açıklama yazma.

Mümkün intent değerleri:
get_time, get_date, get_battery, open_home, press_back, open_notifications, open_recents,
lock_screen, scroll_down, scroll_up, read_screen, volume_up, volume_down, volume_mute,
open_wifi, open_bluetooth, open_settings, open_camera, open_gallery,
open_youtube, open_spotify, open_whatsapp, open_instagram, open_telegram, open_chrome,
search_google, open_maps, set_alarm, set_timer, open_calendar, open_calculator,
open_notes, open_translate, get_weather, open_app, tell_joke, greeting, how_are_you,
thanks, help, chat, unknown

JSON formatı (örnek):
{"intent":"open_youtube","params":{"query":"korku filmleri"},"reply":null}
{"intent":"get_time","params":{},"reply":null}
{"intent":"chat","params":{},"reply":"Tabii efendim, yardımcı olayım"}

Kurallar:
- open_youtube / search_google / open_maps / open_app için params.query veya params.name doldur
- chat ise reply alanına kısa Türkçe cevap yaz
- Anlamadıysan intent: unknown
""".trimIndent()

        val body = JSONObject().apply {
            // Groq'da hâlâ çalışan hızlı model
            put("model", "llama-3.1-8b-instant")
            put("temperature", 0.0)
            put("max_tokens", 120)
            put("response_format", JSONObject().put("type", "json_object"))
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

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(stream.reader(Charsets.UTF_8)).readText()

        if (code !in 200..299) {
            Log.e("AiIntent", "HTTP $code → $response")
            // Model adı geçersizse alternatif dene
            if (code == 400 && response.contains("model", ignoreCase = true)) {
                return callGroqWithModel(apiKey, userText, "llama-3.3-70b-versatile")
            }
            return null
        }

        return parseResponse(response)
    }

    private fun callGroqWithModel(apiKey: String, userText: String, model: String): IntentResult? {
        return try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            val systemPrompt = """
Sen JarvisTR intent sınıflandırıcısısın. SADECE JSON döndür.
Format: {"intent":"...","params":{},"reply":null}
Intent örnekleri: get_time, open_youtube, search_google, volume_up, greeting, chat, unknown
""".trimIndent()

            val body = JSONObject().apply {
                put("model", model)
                put("temperature", 0.0)
                put("max_tokens", 100)
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userText))
                })
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(stream.reader(Charsets.UTF_8)).readText()
            if (code !in 200..299) {
                Log.e("AiIntent", "Alt model HTTP $code → $response")
                return null
            }
            parseResponse(response)
        } catch (e: Exception) {
            Log.e("AiIntent", "Alt model hata: ${e.message}")
            null
        }
    }

    private fun parseResponse(response: String): IntentResult? {
        return try {
            val json = JSONObject(response)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            val clean = content
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            Log.d("AiIntent", "AI cevap: $clean")

            val parsed = JSONObject(clean)
            val intent = parsed.optString("intent", "unknown").lowercase().trim()
            val reply = if (parsed.has("reply") && !parsed.isNull("reply")) {
                parsed.optString("reply").takeIf { it.isNotBlank() }
            } else null

            val params = mutableMapOf<String, String>()
            val paramsObj = parsed.optJSONObject("params")
            if (paramsObj != null) {
                paramsObj.keys().forEach { k ->
                    val v = paramsObj.optString(k)
                    if (v.isNotBlank()) params[k] = v
                }
            }

            IntentResult(intent, params, reply)
        } catch (e: Exception) {
            Log.e("AiIntent", "Parse hata: ${e.message} | raw=$response")
            null
        }
    }
}
