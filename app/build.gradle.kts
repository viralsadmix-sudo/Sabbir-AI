plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "bd.rubel.sabbir"
    compileSdk = 35
    defaultConfig { applicationId = "bd.rubel.sabbir"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
