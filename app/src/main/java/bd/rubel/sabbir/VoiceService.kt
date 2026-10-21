package bd.rubel.sabbir

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

    override fun onCreate() {
        super.onCreate()
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
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("bn", "BD"))
                ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ready) ready = true
                say("আমি সাব্বির। বলুন, কী করতে পারি?")
            }
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(this)
                listen()
            }
        } else say("এই ফোনে কথা বোঝার সুবিধা পাওয়া যায়নি।")
    }

    private fun listen() {
        if (handling) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try { recognizer?.startListening(i) } catch (_: Exception) {}
    }

    private fun say(s: String) {
        if (ready) tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "sabbir")
    }

    private fun launch(url: String, spoken: String) {
        handling = true
        say(spoken)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { say("এই অ্যাপটি ফোনে পাওয়া যায়নি।") }
        android.os.Handler(mainLooper).postDelayed({ handling = false; listen() }, 1800)
    }

    private fun handleCommand(raw: String) {
        val spoken = raw.lowercase(Locale.ROOT)
        when {
            spoken.contains("কয়টা বাজে") || spoken.contains("কটা বাজে") || spoken.contains("সময় বল") ->
                say("এখন সময় " + SimpleDateFormat("hh:mm a", Locale("bn", "BD")).format(Date()))
            spoken.contains("আজ কত তারিখ") || spoken.contains("তারিখ বল") ->
                say("আজ " + SimpleDateFormat("d MMMM yyyy", Locale("bn", "BD")).format(Date()))
            spoken.contains("ইউটিউব") -> launch("https://m.youtube.com", "ইউটিউব খুলছি")
            spoken.contains("গুগল") && (spoken.contains("খুল") || spoken.contains("ওপেন")) ->
                launch("https://www.google.com", "গুগল খুলছি")
            spoken.contains("ফেসবুক") -> launch("https://m.facebook.com", "ফেসবুক খুলছি")
            spoken.contains("হোয়াটসঅ্যাপ") || spoken.contains("হোয়াটসঅ্যাপ") ->
                launch("https://wa.me/", "হোয়াটসঅ্যাপ খুলছি")
            spoken.contains("ম্যাপ") || spoken.contains("গুগল ম্যাপ") ->
                launch("https://maps.google.com", "গুগল ম্যাপ খুলছি")
            spoken.contains("সেটিংস") || spoken.contains("সেটিং") -> {
                handling = true
                say("ফোনের সেটিংস খুলছি")
                try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                android.os.Handler(mainLooper).postDelayed({ handling = false; listen() }, 1800)
            }
            spoken.startsWith("সার্চ ") || spoken.startsWith("খুঁজে দাও ") || spoken.startsWith("গুগলে খোঁজ ") -> {
                val query = spoken.removePrefix("সার্চ ").removePrefix("খুঁজে দাও ").removePrefix("গুগলে খোঁজ ").trim()
                if (query.isNotBlank()) launch("https://www.google.com/search?q=" + Uri.encode(query), "গুগলে খুঁজছি")
                else say("কী খুঁজব বলুন।")
            }
            spoken.contains("হ্যালো") || spoken.contains("সাব্বির") || spoken.contains("সাবির") ->
                say("জি, আমি শুনছি। সময় বা তারিখ বলতে, ইউটিউব, গুগল, ফেসবুক, হোয়াটসঅ্যাপ, ম্যাপ বা সেটিংস খুলতে বলতে পারেন।")
            else -> say("কথাটি বুঝতে পারিনি। আপাতত সময়, তারিখ, গুগল সার্চ এবং অ্যাপ খোলার কমান্ড দিতে পারেন।")
        }
    }

    override fun onResults(results: Bundle?) {
        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) handleCommand(spoken)
        if (!handling) listen()
    }
    override fun onError(error: Int) {
        if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS && !handling) {
            android.os.Handler(mainLooper).postDelayed({ listen() }, 700)
        }
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
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
