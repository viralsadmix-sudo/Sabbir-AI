# Sabbir AI — বাংলা ভয়েস সহকারী

Sabbir AI is a separate native Android app. It no longer calls the Rubel Telecom Appwrite function. AI questions go directly from this personal app to Google's Gemini API after the owner saves an API key on the device.

## Features
- Custom blue Sabbir AI app logo and dark-blue home screen
- Bengali speech recognition and spoken responses
- Typed questions with spoken answers
- Direct Google Gemini Flash API connection
- Quick actions for Google Search, YouTube, time/date, and Android Settings
- Clearer error messages for invalid API keys, quota limits, network errors, and server timeouts
- API key saved only in this device's private app preferences; it is not committed to this repository

## First-time AI setup
1. Install and open Sabbir AI.
2. Open the **AI সংযোগ — একবার সেট করুন** section.
3. Open Google AI Studio from the button and create an API key: https://aistudio.google.com/apikey
4. Paste the key into the app and tap **API key সেভ করুন**. You only need to do this once on this phone.
5. Start the voice assistant or type a question.

Gemini API availability and free-tier quotas depend on Google's current account, region, and model policies. Do not share your personal APK with the key embedded in it; the key is stored on the device, not inside the APK.

## Build APK
1. Open the **Actions** tab.
2. Select **Build Sabbir AI APK**.
3. Download the `Sabbir-AI-debug-apk` artifact from the successful run.
4. Extract the ZIP and install the APK on your Android phone.

## Important Android limitations
- Android may stop microphone listening when the app is backgrounded, battery optimization is enabled, or microphone permissions are restricted.
- AI answers require an internet connection.
- This is a personal assistant app. It does not silently control every phone feature; Android permissions and user confirmation remain required.
