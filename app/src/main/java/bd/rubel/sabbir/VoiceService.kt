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
import android.speech.tts.UtteranceProgressListener
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
    private val longTermMemory = ArrayList<String>()
    private val conversationLock = Any()
    private var lastSpokenAnswer = ""
    private var pendingSpeech: String? = null
    // Wait until the startup greeting finishes before opening the microphone.
    @Volatile private var listenAfterGreeting = false

    companion object {
        const val EXTRA_TEXT_COMMAND = "bd.rubel.sabbir.TEXT_COMMAND"
    }

    // Conversation memory is kept locally on this phone and can be exported by the user.
    private fun loadConversationMemory() {
        val saved = getSharedPreferences("sabbir_settings", MODE_PRIVATE)
            .getString("conversation_memory_json", "").orEmpty()
        if (saved.isBlank()) return
        try {
            val items = org.json.JSONArray(saved)
            synchronized(conversationLock) {
                conversation.clear()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val role = item.optString("role")
                    val text = item.optString("text").trim()
                    if ((role == "user" || role == "model") && text.isNotBlank()) {
                        conversation.add(role to text)
                    }
                }
                while (conversation.size > 12) conversation.removeAt(0)
            }
        } catch (_: Exception) {
            // Ignore a damaged local backup; the assistant should still start.
        }
    }

    private fun saveConversationMemory() {
        val items = org.json.JSONArray()
        synchronized(conversationLock) {
            conversation.takeLast(12).forEach { (role, text) ->
                items.put(JSONObject().put("role", role).put("text", text))
            }
        }
        getSharedPreferences("sabbir_settings", MODE_PRIVATE).edit()
            .putString("conversation_memory_json", items.toString()).apply()
    }

    private fun loadLongTermMemory() {
        val saved = getSharedPreferences("sabbir_settings", MODE_PRIVATE)
            .getString("long_term_memory_json", "[]").orEmpty()
        try {
            val items = org.json.JSONArray(saved)
            synchronized(conversationLock) {
                longTermMemory.clear()
                for (i in 0 until items.length()) {
                    val fact = items.optString(i).trim()
                    if (fact.isNotBlank() && !longTermMemory.contains(fact)) longTermMemory.add(fact)
                }
                while (longTermMemory.size > 30) longTermMemory.removeAt(0)
            }
        } catch (_: Exception) {
            // A damaged memory file must not prevent the assistant from starting.
        }
    }

    private fun saveLongTermMemory() {
        val items = org.json.JSONArray()
        synchronized(conversationLock) {
            longTermMemory.takeLast(30).forEach { items.put(it) }
        }
        getSharedPreferences("sabbir_settings", MODE_PRIVATE).edit()
            .putString("long_term_memory_json", items.toString()).apply()
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
        loadConversationMemory()
        loadLongTermMemory()
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
                tts?.setSpeechRate(0.92f)
                tts?.setPitch(1.02f)
                ready = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        handler.post {
                            if (listenAfterGreeting) {
                                listenAfterGreeting = false
                                if (!destroyed && !handling) listen()
                            }
                        }
                    }
                    @Deprecated("Deprecated by Android")
                    override fun onError(utteranceId: String?) {
                        handler.post {
                            if (listenAfterGreeting) {
                                listenAfterGreeting = false
                                if (!destroyed && !handling) listen()
                            }
                        }
                    }
                })
                val queued = pendingSpeech
                pendingSpeech = null
                if (!queued.isNullOrBlank()) {
                    say(queued)
                } else {
                    listenAfterGreeting = true
                    say("আমি সাব্বির। বলুন, কী করতে পারি?")
                }
            }
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(this)
            }
            // Listening starts after the TTS completion callback, not during the greeting.
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
        if (destroyed) return
        if (!ready || tts == null) {
            pendingSpeech = message
            return
        }
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

        // Only save durable facts when the user explicitly asks; do not guess private details.
        val rememberPrefix = listOf("আমার সম্পর্কে মনে রাখো", "এটা মনে রেখো", "এটা মনে রাখো", "মনে রেখো", "মনে রাখো", "শিখে রাখো")
            .firstOrNull { spoken.startsWith(it) }
        if (rememberPrefix != null) {
            val fact = raw.trim().replaceFirst(
                Regex("^(আমার সম্পর্কে মনে রাখো|এটা মনে রেখো|এটা মনে রাখো|মনে রেখো|মনে রাখো|শিখে রাখো)\\s*", RegexOption.IGNORE_CASE),
                ""
            ).trim()
            if (fact.isNotBlank()) {
                synchronized(conversationLock) {
                    if (!longTermMemory.contains(fact)) longTermMemory.add(fact)
                    while (longTermMemory.size > 30) longTermMemory.removeAt(0)
                }
                saveLongTermMemory()
                say("ঠিক আছে, ফোনের মেমোরিতে মনে রাখলাম।")
            } else {
                say("কোন তথ্যটি মনে রাখব, সেটি বলুন।")
            }
            resumeListening()
            return
        }

        // Use narrow intent phrases: broad words like "সময়" or "তারিখ" can be part of ordinary questions.
        val asksTime = hasAny("কয়টা বাজে", "কয়টা বাজে", "কটা বাজে", "কতটা বাজে", "এখন সময় কত", "এখন সময় কত", "এখন কয়টা", "এখন কয়টা", "টাইম কত", "what time is it")
        val asksDate = hasAny("আজকের তারিখ", "আজ কত তারিখ", "আজকে কত তারিখ", "তারিখ কত", "আজ কী বার", "আজ কি বার", "what is the date")
        val asksOpen = hasAny("খুলে দাও", "খুলে", "খুল", "চালু কর", "ওপেন", "open", "খুলুন", "খোলো")
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
            site == "youtube" && asksOpen -> launchApp("com.google.android.youtube", "https://m.youtube.com", "ইউটিউব অ্যাপ খুলছি")
            site == "facebook" && asksOpen -> launchApp("com.facebook.katana", "https://m.facebook.com", "ফেসবুক খুলছি")
            site == "whatsapp" && asksOpen -> launchApp("com.whatsapp", "https://wa.me/", "হোয়াটসঅ্যাপ খুলছি")
            site == "maps" && asksOpen -> launchApp("com.google.android.apps.maps", "https://maps.google.com", "গুগল ম্যাপ খুলছি")
            (site == "browser" || site == "google") && asksOpen -> launch("https://www.google.com", "গুগল খুলছি")
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
            val groqKey = prefs.getString("groq_api_key", "").orEmpty().trim()
            val geminiKey = prefs.getString("gemini_api_key", "").orEmpty().trim()
            val savedFacts = synchronized(conversationLock) { longTermMemory.takeLast(30).toList() }
            val memoryContext = if (savedFacts.isEmpty()) "" else "\\n\\nব্যবহারকারীর ফোনে সংরক্ষিত দীর্ঘমেয়াদি মেমোরি (প্রাসঙ্গিক হলে ব্যবহার করবে):\\n" +
                savedFacts.joinToString("\\n") { "- $it" }
            val systemPrompt = "তোমার নাম সাব্বির। তুমি ব্যবহারকারীর ব্যক্তিগত বাংলা AI সহকারী। প্রশ্নের সঙ্গে সরাসরি সম্পর্কিত, সঠিক, পরিষ্কার ও স্বাভাবিক উত্তর দাও। বাংলায় বললে সহজ, কথ্য ও স্পষ্ট বাংলায় উত্তর দেবে; বাক্য ছোট রাখবে, অপ্রয়োজনীয় ইংরেজি মেশাবে না, অকারণে পরিচয় দেবে না, একই কথা বারবার বলবে না। ভয়েস উত্তরে আগে মূল উত্তরটি বলবে, তারপর দরকার হলে সংক্ষিপ্ত ব্যাখ্যা দেবে। আগের কথোপকথনের প্রসঙ্গ বুঝবে। ব্যবহারকারী স্পষ্টভাবে মনে রাখতে বললে সংরক্ষিত মেমোরি ব্যবহার করবে। সাধারণ জ্ঞান, বিজ্ঞান, ইতিহাস, সংস্কৃতি, প্রযুক্তি এবং অন্য মানুষ বা জনপরিচিত ব্যক্তিদের সম্পর্কে প্রশ্নেরও যথাসম্ভব উত্তর দেবে। সাম্প্রতিক বা যাচাই-না-করা তথ্য নিশ্চিতভাবে বানিয়ে বলবে না; অনিশ্চয়তা জানাবে। ফোনে বাস্তবে কোনো কাজ সম্পন্ন না করলে কখনো কাজ হয়েছে বলে দাবি করবে না." + memoryContext
            try {
                // First try Vireonix: OpenAI-compatible online AI that currently needs no API key.
                // It is a third-party service and may impose fair-use limits or become unavailable.
                try {
                    val messages = org.json.JSONArray()
                    messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
                    synchronized(conversationLock) {
                        conversation.takeLast(8).forEach { (role, text) ->
                            messages.put(JSONObject().put("role", if (role == "model") "assistant" else "user").put("content", text))
                        }
                    }
                    messages.put(JSONObject().put("role", "user").put("content", raw))
                    val body = JSONObject().put("model", "auto").put("messages", messages)
                        .put("temperature", 0.35).put("max_tokens", 900).toString()
                    val conn = (URL("https://vireonix.ai/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 10000
                        readTimeout = 30000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Accept", "application/json")
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
                        if (answer.isNullOrBlank()) failure = "API key ছাড়া AI থেকে খালি উত্তর এসেছে।"
                    } else {
                        val detail = json?.optJSONObject("error")?.optString("message").orEmpty()
                            .ifBlank { responseText }.replace(Regex("\\s+"), " ").take(120)
                        failure = "ফ্রি AI সংযোগে সমস্যা ($status): $detail"
                    }
                } catch (err: Exception) {
                    failure = "API key ছাড়া AI-তে সংযোগ হয়নি: " + (err.message.orEmpty().take(80))
                }


                // Groq fallback: OpenAI-compatible chat completions.
                if (answer.isNullOrBlank() && groqKey.isNotBlank()) {
                    try {
                        val messages = org.json.JSONArray()
                        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
                        synchronized(conversationLock) {
                            conversation.takeLast(8).forEach { (role, text) ->
                                messages.put(JSONObject().put("role", if (role == "model") "assistant" else "user").put("content", text))
                            }
                        }
                        messages.put(JSONObject().put("role", "user").put("content", raw))
                        val body = JSONObject().put("model", "llama-3.3-70b-versatile")
                            .put("messages", messages).put("temperature", 0.35).put("max_tokens", 900).toString()
                        val conn = (URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 12000
                            readTimeout = 35000
                            doOutput = true
                            setRequestProperty("Authorization", "Bearer $groqKey")
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            setRequestProperty("Accept", "application/json")
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
                            if (answer.isNullOrBlank()) failure = "Groq থেকে খালি উত্তর এসেছে।"
                        } else {
                            val detail = json?.optJSONObject("error")?.optString("message").orEmpty()
                                .ifBlank { responseText }.replace(Regex("\\s+"), " ").take(160)
                            failure = when (status) {
                                401, 403 -> "Groq API key ভুল অথবা অনুমতি নেই ($status)।"
                                429 -> "Groq ব্যবহারের সীমা পূর্ণ হয়েছে ($status)।"
                                else -> "Groq সংযোগে সমস্যা ($status): $detail"
                            }
                        }
                    } catch (err: Exception) {
                        failure = "Groq-এ সংযোগ হয়নি: " + err.message.orEmpty().take(80)
                    }
                }

                // If earlier providers fail, try saved OpenRouter and Gemini providers.
                if (answer.isNullOrBlank() && openRouterKey.isNotBlank()) {
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
                    try {
                        // Gemini 3.8 Flash uses the Interactions API.
                        val endpoint = "https://generativelanguage.googleapis.com/v1beta/interactions"
                        val inputText = buildString {
                            append("System instructions:\\n")
                            append(systemPrompt)
                            append("\\n\\nConversation so far:\\n")
                            synchronized(conversationLock) {
                                conversation.takeLast(8).forEach { (role, text) ->
                                    append(if (role == "model") "Assistant: " else "User: ")
                                    append(text)
                                    append("\\n")
                                }
                            }
                            append("\\nCurrent user message:\\n")
                            append(raw)
                        }
                        val bodyJson = JSONObject()
                            .put("model", "gemini-3.8-flash")
                            .put("input", inputText)
                            .put("generation_config", JSONObject().put("thinking_level", "low"))
                            .toString()
                        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 12000
                            readTimeout = 45000
                            doOutput = true
                            setRequestProperty("x-goog-api-key", geminiKey)
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            setRequestProperty("Accept", "application/json")
                        }
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
                            answer = json?.optString("output_text")?.trim()
                                ?.takeIf { it.isNotBlank() && it != "null" }
                            if (answer.isNullOrBlank()) {
                                val steps = json?.optJSONArray("steps")
                                if (steps != null) {
                                    val pieces = ArrayList<String>()
                                    for (i in 0 until steps.length()) {
                                        val step = steps.optJSONObject(i) ?: continue
                                        if (step.optString("type") != "model_output") continue
                                        val parts = step.optJSONArray("content") ?: continue
                                        for (j in 0 until parts.length()) {
                                            val piece = parts.optJSONObject(j)?.optString("text").orEmpty().trim()
                                            if (piece.isNotBlank()) pieces.add(piece)
                                        }
                                    }
                                    answer = pieces.joinToString("\\n").takeIf { it.isNotBlank() }
                                }
                            }
                            if (answer.isNullOrBlank()) failure = "Gemini থেকে খালি উত্তর এসেছে।"
                        } else {
                            val detail = json?.optJSONObject("error")?.optString("message").orEmpty()
                                .ifBlank { responseText }.replace(Regex("\\s+"), " ").take(180)
                            failure = when (status) {
                                400, 404 -> "Gemini মডেল/API অনুরোধে সমস্যা ($status): $detail"
                                401, 403 -> "Gemini API key ভুল অথবা অনুমতি নেই ($status)।"
                                429 -> "Gemini-এর ব্যবহারসীমা পূর্ণ হয়েছে। পরে আবার চেষ্টা করুন।"
                                502, 503 -> "Gemini সার্ভার এখন ব্যস্ত ($status)।"
                                else -> "Gemini সার্ভার ত্রুটি ($status): $detail"
                            }
                        }
                    } catch (err: Exception) {
                        failure = "Gemini-তে সংযোগ হয়নি: " + (err.message.orEmpty().take(100))
                    }
                }
                if (answer.isNullOrBlank() && openRouterKey.isNotBlank() && geminiKey.isNotBlank()) {
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
                        saveConversationMemory()
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
