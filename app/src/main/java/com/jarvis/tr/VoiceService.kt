package com.jarvis.tr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

class VoiceService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TtsManager
    private lateinit var commandProcessor: CommandProcessor
    private lateinit var prefs: PrefsManager
    private var listening = false
    private var speaking = false
    private val handler = Handler(Looper.getMainLooper())
    private var restartRunnable: Runnable? = null

    companion object {
        const val CHANNEL_ID = "jarvis_channel"
        const val NOTIF_ID = 1001
        @Volatile
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = PrefsManager(this)
        tts = TtsManager(this)
        commandProcessor = CommandProcessor(this, tts)
        createChannel()
        startForeground(NOTIF_ID, buildNotification("JARVIS dinliyor"))
        setupRecognizer()
        handler.postDelayed({ startListening() }, 500)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "JARVIS", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Sesli asistan servisi"
            ch.setShowBadge(false)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(text))
        } catch (e: Exception) {}
    }

    private fun setupRecognizer() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateNotification("Dinliyorum...")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                listening = false
                if (!speaking) {
                    scheduleRestart(700)
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val said = matches[0]
                    updateNotification("Duydum: $said")
                    speaking = true
                    commandProcessor.process(said) {
                        speaking = false
                        scheduleRestart(600)
                    }
                } else {
                    scheduleRestart(600)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!prefs.autoRestart) return
        restartRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (!speaking) startListening()
        }
        restartRunnable = r
        handler.postDelayed(r, delayMs)
    }

    private fun startListening() {
        if (listening || speaking) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        try {
            recognizer?.startListening(intent)
            listening = true
            updateNotification("JARVIS dinliyor")
        } catch (e: Exception) {
            listening = false
            scheduleRestart(1500)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!listening) startListening()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        restartRunnable?.let { handler.removeCallbacks(it) }
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (e: Exception) {}
        tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}