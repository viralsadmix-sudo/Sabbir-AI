package bd.rubel.sabbir

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceService : Service(), RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ready = false
    override fun onCreate() {
        super.onCreate()
        val channel = "sabbir_voice"
        if (Build.VERSION.SDK_INT >= 26) { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "সাব্বির AI", NotificationManager.IMPORTANCE_LOW)) }
        val notification: Notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel).setContentTitle("সাব্বির AI চালু").setContentText("ভয়েস কমান্ডের জন্য প্রস্তুত").setSmallIcon(android.R.drawable.ic_btn_speak_now).build() else Notification.Builder(this).setContentTitle("সাব্বির AI চালু").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        startForeground(101, notification)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) { tts?.language = Locale("bn", "BD"); ready = true; say("আমি সাব্বির। বলুন, কী সাহায্য লাগবে?") } }
        if (SpeechRecognizer.isRecognitionAvailable(this)) { recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this); listen() } } else say("এই ফোনে speech recognition পাওয়া যায়নি।")
    }
    private fun listen() { val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false) }; try { recognizer?.startListening(i) } catch (_: Exception) {} }
    private fun say(s: String) { if (ready) tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "sabbir") }
    override fun onResults(results: Bundle?) { val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase(Locale.ROOT).orEmpty(); when { spoken.contains("হ্যালো সাব্বির") || spoken.contains("হ্যালো সাবির") || spoken.contains("hello sabbir") -> say("জি, বলুন।"); spoken.contains("কয়টা বাজে") || spoken.contains("কটা বাজে") -> say("এখন সময় " + java.text.SimpleDateFormat("hh:mm a", Locale("bn", "BD")).format(java.util.Date())); spoken.contains("ইউটিউব") -> { say("ইউটিউব খুলছি"); try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://m.youtube.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }; else -> say("আমি শুনেছি। এখনো সব কমান্ড শেখানো হয়নি।") }; listen() }
    override fun onError(error: Int) { if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) listen() }
    override fun onReadyForSpeech(params: Bundle?) {}; override fun onBeginningOfSpeech() {}; override fun onRmsChanged(rmsdB: Float) {}; override fun onBufferReceived(buffer: ByteArray?) {}; override fun onEndOfSpeech() {}; override fun onPartialResults(partialResults: Bundle?) {}; override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { recognizer?.destroy(); recognizer = null; tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
