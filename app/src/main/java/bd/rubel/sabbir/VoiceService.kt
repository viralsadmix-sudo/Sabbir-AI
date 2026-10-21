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
            else ->
                say("আমি শুনেছি: $raw। এখনো এই কাজটি সরাসরি করতে পারছি না। গুগলে খুঁজতে বলুন, অথবা বলুন ইউটিউব খুলে দাও, সময় বল, কিংবা তারিখ বল।")
        }
        resumeListening()
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
