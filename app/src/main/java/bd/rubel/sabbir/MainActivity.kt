package bd.rubel.sabbir

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 60, 32, 32) }
        layout.addView(TextView(this).apply { text = "সাব্বির AI"; textSize = 30f })
        layout.addView(TextView(this).apply { text = "আপনার ব্যক্তিগত বাংলা ভয়েস সহকারী"; textSize = 18f })
        layout.addView(TextView(this).apply { text = "শুরু করতে মাইক্রোফোন অনুমতি দিন। Android-এর নিয়মে সবসময় শোনা নিশ্চিত নয়।" })
        layout.addView(Button(this).apply { text = "ভয়েস সহকারী চালু করুন"; setOnClickListener { startAssistant() } })
        layout.addView(Button(this).apply { text = "বন্ধ করুন"; setOnClickListener { stopService(Intent(this@MainActivity, VoiceService::class.java)) } })
        setContentView(layout)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
    }
    private fun startAssistant() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10); return }
        val i = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
    }
}
