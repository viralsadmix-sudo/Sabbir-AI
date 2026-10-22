package bd.rubel.sabbir

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_EXPORT_MEMORY = 40
        private const val REQUEST_IMPORT_MEMORY = 41
    }

    private val navy = Color.rgb(5, 17, 39)
    private val panel = Color.rgb(12, 32, 66)
    private val cyan = Color.rgb(0, 200, 255)
    private lateinit var status: TextView
    private lateinit var question: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_sabbir_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(84), dp(84)))
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titleColumn.addView(TextView(this).apply {
            text = "Sabbir AI"
            textSize = 29f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        titleColumn.addView(TextView(this).apply {
            text = "আপনার ব্যক্তিগত বাংলা সহকারী"
            textSize = 14f
            setTextColor(cyan)
        })
        header.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header)
        content.addView(TextView(this).apply {
            text = "কথা বলুন • প্রশ্ন করুন • কাজ শুরু করুন"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, dp(18), 0, dp(14))
        })

        val statusCard = card()
        status = TextView(this).apply {
            text = "● প্রস্তুত — ভয়েস সহকারী চালু করুন"
            textSize = 14f
            setTextColor(Color.rgb(139, 240, 208))
        }
        statusCard.addView(status)
        content.addView(statusCard, marginParams(bottom = 14))

        val start = actionButton("🎙  ভয়েস সহকারী চালু করুন", cyan, navy)
        start.setOnClickListener { startAssistant() }
        content.addView(start, marginParams(bottom = 10))
        val stop = actionButton("■  সহকারী বন্ধ করুন", Color.rgb(36, 55, 86), Color.WHITE)
        stop.setOnClickListener {
            stopService(Intent(this, VoiceService::class.java))
            status.text = "সহকারী বন্ধ করা হয়েছে"
            Toast.makeText(this, "সাব্বির AI বন্ধ হয়েছে", Toast.LENGTH_SHORT).show()
        }
        content.addView(stop, marginParams(bottom = 20))

        content.addView(sectionTitle("সাব্বিরকে প্রশ্ন করুন"))
        val chatCard = card()
        question = EditText(this).apply {
            hint = "যেমন: কৃত্রিম বুদ্ধিমত্তা কী?"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(false)
            minLines = 2
            maxLines = 4
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(19, 43, 79), 14)
        }
        chatCard.addView(question, LinearLayout.LayoutParams(-1, -2))
        val send = actionButton("প্রশ্ন পাঠান ও উত্তর শুনুন", cyan, navy)
        send.setOnClickListener {
            val text = question.text.toString().trim()
            if (text.isBlank()) {
                question.error = "প্রথমে প্রশ্ন লিখুন"
            } else {
                val i = Intent(this, VoiceService::class.java)
                    .putExtra(VoiceService.EXTRA_TEXT_COMMAND, text)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
                status.text = "প্রশ্ন পাঠানো হয়েছে — সাব্বির উত্তর দেবে"
                question.text.clear()
            }
        }
        chatCard.addView(send, marginParams(top = 10))
        content.addView(chatCard, marginParams(bottom = 20))

        content.addView(sectionTitle("AI সংযোগ — একবার সেট করুন"))
        val aiCard = card()
        aiCard.addView(TextView(this).apply {
            text = "Google AI Studio থেকে বিনামূল্যের Gemini API key তৈরি করে এখানে সেভ করুন। এরপর সাব্বির সরাসরি Gemini-তে প্রশ্ন পাঠাবে; Rubel Telecom/Appwrite সার্ভার লাগবে না।"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(10))
        })
        val keyInput = EditText(this).apply {
            hint = "Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(19, 43, 79), 14)
            setText(getSharedPreferences("sabbir_settings", MODE_PRIVATE).getString("gemini_api_key", ""))
        }
        aiCard.addView(keyInput, LinearLayout.LayoutParams(-1, -2))
        val saveKey = actionButton("API key সেভ করুন", cyan, navy)
        saveKey.setOnClickListener {
            val key = keyInput.text.toString().trim()
            if (key.isBlank()) {
                keyInput.error = "Gemini API key দিন"
            } else {
                getSharedPreferences("sabbir_settings", MODE_PRIVATE).edit().putString("gemini_api_key", key).apply()
                status.text = "Gemini API key সেভ হয়েছে — প্রথম প্রশ্নে সংযোগ পরীক্ষা হবে"
                Toast.makeText(this, "API key সেভ হয়েছে; এটি এখনো সংযোগ সফল হওয়ার প্রমাণ নয়", Toast.LENGTH_LONG).show()
            }
        }
        aiCard.addView(saveKey, marginParams(top = 10))
        val getKey = actionButton("Google AI Studio খুলুন", Color.rgb(36, 55, 86), Color.WHITE)
        getKey.setOnClickListener { open("https://aistudio.google.com/apikey", "Google AI Studio") }
        aiCard.addView(getKey, marginParams(top = 8))
        content.addView(aiCard, marginParams(bottom = 20))

        content.addView(sectionTitle("OpenRouter সংযোগ — বিকল্প AI"))
        val openRouterCard = card()
        openRouterCard.addView(TextView(this).apply {
            text = "Gemini ব্যস্ত বা কোটা শেষ হলে OpenRouter ব্যবহার করা হবে। OpenRouter থেকে API key তৈরি করে এখানে সেভ করুন। প্রথমে বিনামূল্যের মডেল চেষ্টা হবে; ফ্রি ব্যবহারের সীমা থাকতে পারে।"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(10))
        })
        val openRouterInput = EditText(this).apply {
            hint = "OpenRouter API key (sk-or-...)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(19, 43, 79), 14)
            setText(getSharedPreferences("sabbir_settings", MODE_PRIVATE).getString("openrouter_api_key", ""))
        }
        openRouterCard.addView(openRouterInput, LinearLayout.LayoutParams(-1, -2))
        val saveOpenRouter = actionButton("OpenRouter key সেভ করুন", cyan, navy)
        saveOpenRouter.setOnClickListener {
            val key = openRouterInput.text.toString().trim()
            if (key.isBlank()) {
                openRouterInput.error = "OpenRouter API key দিন"
            } else {
                getSharedPreferences("sabbir_settings", MODE_PRIVATE).edit().putString("openrouter_api_key", key).apply()
                status.text = "OpenRouter API key সেভ হয়েছে — প্রথম প্রশ্নে সংযোগ পরীক্ষা হবে"
                Toast.makeText(this, "OpenRouter key সেভ হয়েছে; প্রথম প্রশ্নে সংযোগ পরীক্ষা হবে", Toast.LENGTH_LONG).show()
            }
        }
        openRouterCard.addView(saveOpenRouter, marginParams(top = 10))
        val getOpenRouter = actionButton("OpenRouter খুলুন", Color.rgb(36, 55, 86), Color.WHITE)
        getOpenRouter.setOnClickListener { open("https://openrouter.ai/keys", "OpenRouter") }
        openRouterCard.addView(getOpenRouter, marginParams(top = 8))
        content.addView(openRouterCard, marginParams(bottom = 20))

        content.addView(sectionTitle("দ্রুত কাজ"))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(quickButton("🔎  Google Search") { open("https://www.google.com", "Google") }, LinearLayout.LayoutParams(0, dp(58), 1f))
        row1.addView(quickButton("▶  YouTube") { open("https://m.youtube.com", "YouTube") }, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(8) })
        grid.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        row2.addView(quickButton("🕒  সময় ও তারিখ") {
            val now = java.text.SimpleDateFormat("hh:mm a, d MMMM yyyy", java.util.Locale("bn", "BD")).format(java.util.Date())
            Toast.makeText(this@MainActivity, "এখন $now", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(0, dp(58), 1f))
        row2.addView(quickButton("⚙  ফোন সেটিংস") {
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(this@MainActivity, "সেটিংস খোলা যায়নি", Toast.LENGTH_SHORT).show() }
        }, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(8) })
        grid.addView(row2)
        content.addView(grid)

        content.addView(sectionTitle("মেমোরি ও USB ব্যাকআপ"))
        val memoryCard = card()
        memoryCard.addView(TextView(this).apply {
            text = "সাম্প্রতিক কথোপকথন এবং তুমি স্পষ্টভাবে ‘মনে রাখো’ বলে শেখানো তথ্য এই ফোনে থাকবে। JSON ব্যাকআপে দুটোই থাকবে, API key থাকবে না। অন্য মানুষ ও সাধারণ বিষয় নিয়েও AI-কে প্রশ্ন করতে পারবে।"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(10))
        })
        val exportMemory = actionButton("মেমোরি ব্যাকআপ তৈরি করুন", cyan, navy)
        exportMemory.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "sabbir-ai-memory.json")
            }
            startActivityForResult(intent, REQUEST_EXPORT_MEMORY)
        }
        memoryCard.addView(exportMemory, marginParams(bottom = 8))
        val importMemory = actionButton("ব্যাকআপ থেকে মেমোরি ফিরিয়ে আনুন", Color.rgb(36, 55, 86), Color.WHITE)
        importMemory.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, REQUEST_IMPORT_MEMORY)
        }
        memoryCard.addView(importMemory)
        content.addView(memoryCard, marginParams(bottom = 20))

        content.addView(TextView(this).apply {
            text = "AI উত্তরের জন্য ইন্টারনেট দরকার। মাইক্রোফোন চালু রাখতে Android-এর অনুমতি ও ব্যাটারি সেটিংস প্রয়োজন হতে পারে।"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(20), 0, 0)
        })
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
    }

    @Deprecated("Uses the compatible document-picker result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_EXPORT_MEMORY -> {
                val prefs = getSharedPreferences("sabbir_settings", MODE_PRIVATE)
                val conversation = org.json.JSONArray(prefs.getString("conversation_memory_json", "[]").orEmpty().ifBlank { "[]" })
                val longTerm = org.json.JSONArray(prefs.getString("long_term_memory_json", "[]").orEmpty().ifBlank { "[]" })
                val backup = org.json.JSONObject()
                    .put("version", 2)
                    .put("conversation", conversation)
                    .put("longTermMemory", longTerm)
                    .toString(2)
                try {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(backup.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("ফাইল লেখা যায়নি")
                    Toast.makeText(this, "মেমোরি ব্যাকআপ তৈরি হয়েছে", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "ব্যাকআপ লেখা যায়নি। আবার চেষ্টা করুন।", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_IMPORT_MEMORY -> {
                try {
                    val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("ফাইল পড়া যায়নি")
                    val parsed = org.json.JSONTokener(raw).nextValue()
                    val source = if (parsed is org.json.JSONArray) parsed
                        else (parsed as? org.json.JSONObject)?.optJSONArray("conversation")
                            ?: throw IllegalArgumentException("ব্যাকআপ ফরম্যাট সঠিক নয়")
                    val clean = org.json.JSONArray()
                    for (i in 0 until source.length()) {
                        val item = source.optJSONObject(i) ?: continue
                        val role = item.optString("role")
                        val text = item.optString("text").trim()
                        if ((role == "user" || role == "model") && text.isNotBlank()) {
                            clean.put(org.json.JSONObject().put("role", role).put("text", text))
                        }
                    }
                    val longTermSource = (parsed as? org.json.JSONObject)?.optJSONArray("longTermMemory") ?: org.json.JSONArray()
                    val cleanLongTerm = org.json.JSONArray()
                    for (i in 0 until longTermSource.length()) {
                        val fact = longTermSource.optString(i).trim()
                        if (fact.isNotBlank()) cleanLongTerm.put(fact)
                    }
                    if (clean.length() == 0 && cleanLongTerm.length() == 0) {
                        throw IllegalArgumentException("ব্যাকআপে মেমোরি নেই")
                    }
                    while (clean.length() > 12) clean.remove(0)
                    while (cleanLongTerm.length() > 30) cleanLongTerm.remove(0)
                    getSharedPreferences("sabbir_settings", MODE_PRIVATE).edit()
                        .putString("conversation_memory_json", clean.toString())
                        .putString("long_term_memory_json", cleanLongTerm.toString())
                        .apply()
                    Toast.makeText(this, "কথোপকথন ও শেখানো মেমোরি ফিরিয়ে আনা হয়েছে। ভয়েস সহকারী বন্ধ করে আবার চালু করুন।", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "এই ব্যাকআপ ফাইলটি সঠিক নয় বা পড়া যায়নি।", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startAssistant() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            status.text = "ভয়েস চালাতে মাইক্রোফোন অনুমতি দিন"
            return
        }
        val i = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        status.text = "● সাব্বির শুনছে — বাংলায় কথা বলুন"
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = rounded(panel, 18)
    }

    private fun actionButton(label: String, bg: Int, fg: Int) = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(fg)
        background = rounded(bg, 16)
        minHeight = dp(50)
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }

    private fun quickButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(17, 42, 79), 14)
        setOnClickListener { click() }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(10))
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), if (color == cyan) cyan else Color.rgb(29, 64, 111))
    }

    private fun marginParams(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        if (top != 0) topMargin = dp(top)
        if (bottom != 0) bottomMargin = dp(bottom)
    }

    private fun open(url: String, label: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "$label খোলা যায়নি", Toast.LENGTH_SHORT).show() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
