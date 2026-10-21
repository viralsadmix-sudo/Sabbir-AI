package bd.rubel.sabbir

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import kotlin.concurrent.thread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceService : Service(), RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ready = false
    private var handling = false
    private var destroyed = false
    private val aiEndpoint = "https://starter-function-bs1o.nyc.appwrite.run"
    private lateinit var handler: Handler

    companion object {
        const val EXTRA_TEXT_COMMAND = "bd.rubel.sabbir.TEXT_COMMAND"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val typedCommand = intent?.getStringExtra(EXTRA_TEXT_COMMAND)?.trim().orEmpty()
        if (typedCommand.isNotBlank()) {
            handler.postDelayed({
                if (!destroyed) handleCommand(typedCommand)
            }, 900)
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        // Service context is available only after super.onCreate(); avoid crashing during construction.
        handler = Handler(mainLooper)
        val channel = "sabbir_voice"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "সাব্বির AI", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channel).setContentTitle("সাব্বির AI চালু")
                .setContentText("ভয়েস কমান্ডের জন্য প্রস্তুত")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        } else {
            Notification.Builder(this).setContentTitle("সাব্বির AI চালু")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        }
        startForeground(101, notification)

        // Keep the simple, known-working TTS initialization flow.
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale("bn", "BD"))
                ready = true
                say("আমি সাব্বির। বলুন, কী করতে পারি?")
            }
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(this)
            }
            handler.postDelayed({ listen() }, 1800)
        } else {
            handler.postDelayed({ say("এই ফোনে কথা বোঝার সুবিধা পাওয়া যায়নি।") }, 500)
        }
    }

    private fun listen() {
        if (destroyed || handling) return
        val speechRecognizer = recognizer ?: return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechRecognizer.startListening(i)
        } catch (_: Exception) {
            handler.postDelayed({ listen() }, 1200)
        }
    }

    private fun say(message: String) {
        if (!ready || destroyed) return
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "sabbir_" + System.currentTimeMillis())
    }

    private fun resumeListening(delay: Long = 1800) {
        handler.postDelayed({ if (!destroyed && !handling) listen() }, delay)
    }

    private fun launch(url: String, message: String) {
        handling = true
        say(message)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            say("এই লিংকটি খোলা যায়নি।")
        }
        handler.postDelayed({ handling = false; listen() }, 2200)
    }

    private fun handleCommand(raw: String) {
        // Normalize common Bengali speech-recognition variations before matching.
        val spoken = raw.lowercase(Locale.ROOT)
            .replace("য়", "য়")
            .replace("ী", "ি")
            .replace("ূ", "ু")
            .replace(Regex("[।!?.,;:]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (spoken.isBlank()) {
            say("আপনার কথা পরিষ্কার শুনতে পাইনি। একটু কাছে থেকে আবার বলুন।")
            resumeListening(700)
            return
        }

        fun hasAny(vararg words: String): Boolean = words.any { spoken.contains(it) }
        val asksTime = hasAny("কয়টা", "কটা", "কতটা বাজে", "সময়", "টাইম", "time")
        val asksDate = hasAny("তারিখ", "আজকে কত", "আজ কত", "date", "দিন কী")
        val asksOpen = hasAny("খুলে দাও", "খুলে", "খুল", "চালু কর", "ওপেন", "open", "দেখাও", "যাও")
        val site = when {
            hasAny("ইউটিউব", "youtube") -> "youtube"
            hasAny("ফেসবুক", "facebook") -> "facebook"
            hasAny("হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ", "হোয়াটসাপ", "whatsapp") -> "whatsapp"
            hasAny("গুগল ম্যাপ", "গুগলম্যাপ", "ম্যাপ", "maps") -> "maps"
            hasAny("ক্রোম", "chrome", "ব্রাউজার", "ইন্টারনেট") -> "browser"
            hasAny("গুগল", "google") && asksOpen -> "google"
            else -> ""
        }

        when {
            asksTime -> say("এখন সময় " + SimpleDateFormat("hh:mm a", Locale("bn", "BD")).format(Date()))
            asksDate -> say("আজ " + SimpleDateFormat("d MMMM yyyy", Locale("bn", "BD")).format(Date()))
            hasAny("সেটিংস", "সেটিং", "settings") && asksOpen -> {
                handling = true
                say("ফোনের সেটিংস খুলছি")
                try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { say("সেটিংস খোলা যায়নি।") }
                handler.postDelayed({ handling = false; listen() }, 2200)
            }
            site == "youtube" -> launch("https://m.youtube.com", "ইউটিউব খুলছি")
            site == "facebook" -> launch("https://m.facebook.com", "ফেসবুক খুলছি")
            site == "whatsapp" -> launch("https://wa.me/", "হোয়াটসঅ্যাপ খুলছি")
            site == "maps" -> launch("https://maps.google.com", "গুগল ম্যাপ খুলছি")
            site == "browser" || site == "google" -> launch("https://www.google.com", "গুগল খুলছি")
            hasAny("সার্চ", "খুঁজে দাও", "খুঁজে দেখ", "খোঁজ", "অনুসন্ধান", "search") -> {
                val query = spoken
                    .replace(Regex("^(দয়া করে |দয়া করে |একটু )"), "")
                    .replace(Regex("^(গুগলে |গুগল এ |google এ )"), "")
                    .replace(Regex("^(সার্চ করে |সার্চ |খুঁজে দাও |খুঁজে দেখ |খুঁজে |খোঁজ |অনুসন্ধান কর |search )"), "")
                    .replace(Regex("( করে দাও| করে দেখাও| দাও| দেখাও)$"), "")
                    .trim()
                if (query.isNotBlank()) launch("https://www.google.com/search?q=" + Uri.encode(query), "খুঁজে দেখছি: $query")
                else say("কী খুঁজে দেব? বিষয়টি বলুন।")
            }
            hasAny("হ্যালো", "হ্যাল", "সাব্বির", "সাবির", "hello", "hi") ->
                say("জি, বলুন। সময় বা তারিখ জানতে, ইউটিউব খুলতে, কিংবা যেকোনো কিছু গুগলে খুঁজতে বলতে পারেন।")
            else -> {
                askAi(raw)
                return
            }
        }
        resumeListening()
    }

    private fun askAi(raw: String) {
        handling = true
        say("একটু ভাবছি।")
        thread(name = "sabbir-ai-request") {
            var answer: String? = null
            var failure: String? = null
            var connection: HttpURLConnection? = null
            try {
                val apiKey = getSharedPreferences("sabbir_settings", MODE_PRIVATE)
                    .getString("gemini_api_key", "").orEmpty().trim()
                if (apiKey.isBlank()) {
                    failure = "AI চালু করতে অ্যাপের AI সংযোগ অংশে Google AI Studio API key একবার সেভ করুন।"
                } else {
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + Uri.encode(apiKey)
                    connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 12000
                        readTimeout = 35000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Accept", "application/json")
                    }
                    val body = JSONObject().apply {
                        put("systemInstruction", JSONObject().put("parts", org.json.JSONArray().put(
                            JSONObject().put("text", "তোমার নাম সাব্বির। তুমি ব্যবহারকারীর ব্যক্তিগত বাংলা AI সহকারী। বাংলায় স্বাভাবিক, সহায়ক ও সংক্ষিপ্ত উত্তর দাও। ফোনে বাস্তবে কাজ না করলে কাজ হয়েছে বলে দাবি করবে না।"))
                        ))
                        put("contents", org.json.JSONArray().put(
                            JSONObject().put("role", "user").put("parts", org.json.JSONArray().put(JSONObject().put("text", raw)))
                        ))
                        put("generationConfig", JSONObject().put("temperature", 0.6).put("maxOutputTokens", 700))
                    }.toString()
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val json = try { JSONObject(responseText) } catch (_: Exception) { null }
                    if (status in 200..299) {
                        val candidates = json?.optJSONArray("candidates")
                        val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                        if (parts != null) {
                            val pieces = ArrayList<String>()
                            for (i in 0 until parts.length()) {
                                val piece = parts.optJSONObject(i)?.optString("text").orEmpty().trim()
                                if (piece.isNotBlank()) pieces.add(piece)
                            }
                            answer = pieces.joinToString("\n").takeIf { it.isNotBlank() }
                        }
                        if (answer.isNullOrBlank()) {
                            val blockReason = json?.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                            failure = if (blockReason.isNotBlank()) "এই প্রশ্নটির উত্তর দেওয়া যায়নি: $blockReason" else "AI থেকে খালি উত্তর এসেছে। অন্যভাবে প্রশ্ন করুন।"
                        }
                    } else {
                        val detail = json?.optJSONObject("error")?.optString("message").orEmpty().ifBlank { responseText }
                            .replace(Regex("\\s+"), " ").take(220)
                        failure = when (status) {
                            400 -> "প্রশ্ন বা API অনুরোধে সমস্যা (400): $detail"
                            401, 403 -> "Gemini API key ভুল, নিষ্ক্রিয়, অথবা অনুমতি নেই ($status)।"
                            429 -> "বিনামূল্যের AI ব্যবহারের সীমা আপাতত শেষ হয়েছে (429)। পরে আবার চেষ্টা করুন।"
                            502, 503 -> "AI সার্ভার এখন ব্যস্ত ($status)। একটু পরে আবার চেষ্টা করুন।"
                            504 -> "AI সার্ভার সময়মতো উত্তর দেয়নি (504)। কিছুক্ষণ পর আবার চেষ্টা করুন।"
                            else -> "AI সার্ভার ত্রুটি ($status): $detail"
                        }
                    }
                }
            } catch (_: java.net.SocketTimeoutException) {
                failure = "AI উত্তর দিতে বেশি সময় নিয়েছে। ইন্টারনেট পরীক্ষা করে আবার চেষ্টা করুন।"
            } catch (_: Exception) {
                failure = "Gemini-র সঙ্গে সংযোগ হচ্ছে না। ইন্টারনেট ও API key পরীক্ষা করুন।"
            } finally {
                connection?.disconnect()
            }
            handler.post {
                if (destroyed) return@post
                handling = false
                val cleanAnswer = answer?.trim()?.take(2800)
                when {
                    !cleanAnswer.isNullOrBlank() -> say(cleanAnswer)
                    failure != null -> say(failure!!)
                    else -> say("AI থেকে উত্তর আসেনি। অন্যভাবে প্রশ্ন করুন।")
                }
                resumeListening(4000)
            }
        }
    }

    override fun onResults(results: Bundle?) {
        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) handleCommand(spoken) else resumeListening(500)
    }

    override fun onError(error: Int) {
        if (destroyed || handling) return
        // Recognition can time out; restart quietly without stopping the assistant.
        resumeListening(if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) 3000 else 900)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
