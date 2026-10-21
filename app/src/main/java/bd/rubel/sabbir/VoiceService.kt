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
    private lateinit var handler: Handler
    private val conversation = ArrayList<Pair<String, String>>()
    private val conversationLock = Any()
    private var lastSpokenAnswer = ""

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

    // Prefer the real installed Android app; use the website only as a fallback.
    private fun launchApp(packageName: String, fallbackUrl: String, message: String) {
        handling = true
        try {
            val appIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (appIntent != null) {
                say(message)
                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(appIntent)
            } else {
                handling = false
                launch(fallbackUrl, message)
                return
            }
        } catch (_: Exception) {
            handling = false
            launch(fallbackUrl, message)
            return
        }
        handler.postDelayed({ handling = false; listen() }, 2500)
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
            site == "youtube" -> launchApp("com.google.android.youtube", "https://m.youtube.com", "ইউটিউব অ্যাপ খুলছি")
            site == "facebook" -> launchApp("com.facebook.katana", "https://m.facebook.com", "ফেসবুক খুলছি")
            site == "whatsapp" -> launchApp("com.whatsapp", "https://wa.me/", "হোয়াটসঅ্যাপ খুলছি")
            site == "maps" -> launchApp("com.google.android.apps.maps", "https://maps.google.com", "গুগল ম্যাপ খুলছি")
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
            spoken in setOf("হ্যালো", "হ্যাল", "হ্যালো সাব্বির", "সাব্বির", "সাবির", "হাই", "hello", "hello sabbir", "hi", "সাব্বির শুনছো", "সাব্বির শুনতে পাচ্ছো") ->
                say("জি, শুনছি। বলুন, কী জানতে চান?")
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
            val prefs = getSharedPreferences("sabbir_settings", MODE_PRIVATE)
            val openRouterKey = prefs.getString("openrouter_api_key", "").orEmpty().trim()
            val geminiKey = prefs.getString("gemini_api_key", "").orEmpty().trim()
            val systemPrompt = "তোমার নাম সাব্বির। তুমি ব্যবহারকারীর ব্যক্তিগত বাংলা AI সহকারী। প্রশ্নের সঙ্গে সরাসরি সম্পর্কিত, সঠিক ও স্বাভাবিক উত্তর দাও। বাংলায় বললে সহজ বাংলায় উত্তর দেবে; অকারণে পরিচয় দেবে না, একই কথা বারবার বলবে না। আগের কথোপকথনের প্রসঙ্গ বুঝবে। নিশ্চিত না হলে অনিশ্চয়তা জানাবে। ফোনে বাস্তবে কোনো কাজ সম্পন্ন না করলে কখনো কাজ হয়েছে বলে দাবি করবে না।"
            try {
                if (openRouterKey.isNotBlank()) {
                    try {
                        val messages = org.json.JSONArray()
                        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
                        synchronized(conversationLock) {
                            conversation.takeLast(8).forEach { (role, text) ->
                                messages.put(JSONObject().put("role", if (role == "model") "assistant" else "user").put("content", text))
                            }
                        }
                        messages.put(JSONObject().put("role", "user").put("content", raw))
                        val body = JSONObject().put("model", "openrouter/free").put("messages", messages)
                            .put("temperature", 0.35).put("max_tokens", 900).toString()
                        val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 12000
                            readTimeout = 35000
                            doOutput = true
                            setRequestProperty("Authorization", "Bearer $openRouterKey")
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("HTTP-Referer", "https://github.com/viralsadmix-sudo/Sabbir-AI")
                            setRequestProperty("X-Title", "Sabbir AI")
                        }
                        val responseText: String
                        val status: Int
                        try {
                            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                            status = conn.responseCode
                            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                            responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        } finally { conn.disconnect() }
                        val json = try { JSONObject(responseText) } catch (_: Exception) { null }
                        if (status in 200..299) {
                            answer = json?.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                                ?.optString("content")?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                            if (answer.isNullOrBlank()) failure = "OpenRouter থেকে খালি উত্তর এসেছে।"
                        } else {
                            val detail = json?.optJSONObject("error")?.optString("message").orEmpty()
                                .ifBlank { responseText }.replace(Regex("\\s+"), " ").take(180)
                            failure = "OpenRouter সংযোগে সমস্যা ($status): $detail"
                        }
                    } catch (err: Exception) {
                        failure = "OpenRouter-এ সংযোগ হয়নি: " + (err.message.orEmpty().take(100))
                    }
                }
                if (answer.isNullOrBlank() && geminiKey.isNotBlank()) {
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=" + Uri.encode(geminiKey)
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 12000
                        readTimeout = 35000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Accept", "application/json")
                    }
                    val bodyJson = JSONObject().apply {
                        put("systemInstruction", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", systemPrompt))))
                        val contents = org.json.JSONArray()
                        synchronized(conversationLock) {
                            conversation.takeLast(8).forEach { (role, text) ->
                                contents.put(JSONObject().put("role", role).put("parts", org.json.JSONArray().put(JSONObject().put("text", text))))
                            }
                        }
                        contents.put(JSONObject().put("role", "user").put("parts", org.json.JSONArray().put(JSONObject().put("text", raw))))
                        put("contents", contents)
                        put("generationConfig", JSONObject().put("temperature", 0.35).put("maxOutputTokens", 900))
                    }.toString()
                    val responseText: String
                    val status: Int
                    try {
                        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(bodyJson) }
                        status = conn.responseCode
                        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                        responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    } finally { conn.disconnect() }
                    val json = try { JSONObject(responseText) } catch (_: Exception) { null }
                    if (status in 200..299) {
                        val parts = json?.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                        if (parts != null) {
                            val pieces = ArrayList<String>()
                            for (i in 0 until parts.length()) {
                                val piece = parts.optJSONObject(i)?.optString("text").orEmpty().trim()
                                if (piece.isNotBlank()) pieces.add(piece)
                            }
                            answer = pieces.joinToString("\n").takeIf { it.isNotBlank() }
                        }
                        if (answer.isNullOrBlank()) failure = "Gemini থেকে খালি উত্তর এসেছে।"
                    } else {
                        val detail = json?.optJSONObject("error")?.optString("message").orEmpty()
                            .ifBlank { responseText }.replace(Regex("\\s+"), " ").take(180)
                        failure = when (status) {
                            401, 403 -> "Gemini API key ভুল অথবা অনুমতি নেই ($status)।"
                            429 -> "Gemini-এর ব্যবহারসীমা পূর্ণ হয়েছে। OpenRouter key সেভ থাকলে সেটি ব্যবহার করুন, নইলে কোটা রিসেট হওয়া পর্যন্ত অপেক্ষা করুন।"
                            502, 503 -> "Gemini সার্ভার এখন ব্যস্ত ($status)।"
                            else -> "Gemini সার্ভার ত্রুটি ($status): $detail"
                        }
                    }
                }
                if (answer.isNullOrBlank() && openRouterKey.isBlank() && geminiKey.isBlank()) {
                    failure = "AI চালু করতে OpenRouter API key অ্যাপের OpenRouter অংশে সেভ করুন। চাইলে Gemini key-ও ব্যবহার করতে পারেন।"
                } else if (answer.isNullOrBlank() && openRouterKey.isNotBlank() && geminiKey.isNotBlank()) {
                    failure = "OpenRouter ও Gemini—দুটিতেই উত্তর আসেনি। " + failure.orEmpty()
                } else if (answer.isNullOrBlank() && openRouterKey.isNotBlank()) {
                    failure = failure ?: "OpenRouter থেকে উত্তর আসেনি। API key এবং free model limit পরীক্ষা করুন।"
                }
            } catch (_: java.net.SocketTimeoutException) {
                failure = "AI উত্তর দিতে বেশি সময় নিয়েছে। ইন্টারনেট পরীক্ষা করে আবার চেষ্টা করুন।"
            } catch (_: Exception) {
                failure = "AI সংযোগে সমস্যা হয়েছে। API key ও ইন্টারনেট পরীক্ষা করুন।"
            }
            handler.post {
                if (destroyed) return@post
                handling = false
                val cleanAnswer = answer?.trim()?.take(2800)
                when {
                    !cleanAnswer.isNullOrBlank() -> {
                        synchronized(conversationLock) {
                            conversation.add("user" to raw)
                            conversation.add("model" to cleanAnswer)
                            while (conversation.size > 12) conversation.removeAt(0)
                        }
                        val finalAnswer = if (cleanAnswer == lastSpokenAnswer) "আগের উত্তরের পুনরাবৃত্তি না করে বলছি: $cleanAnswer" else cleanAnswer
                        lastSpokenAnswer = cleanAnswer
                        say(finalAnswer)
                    }
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
