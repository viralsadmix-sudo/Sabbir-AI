plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "bd.rubel.sabbir"
    compileSdk = 35
    defaultConfig { applicationId = "bd.rubel.sabbir"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    buildTypes {
        getByName("release") {
            // Makes the APK installable for direct testing. Replace with a private
            // release keystore before publishing a production app.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
