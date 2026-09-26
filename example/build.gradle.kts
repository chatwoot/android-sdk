plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
if (file("google-services.json").exists()) { apply(plugin = "com.google.gms.google-services") }
android {
    namespace = "com.chatwoot.example"
    compileSdk = 35
    defaultConfig { applicationId = "com.chatwoot.example"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":sdk"))
    implementation("com.google.firebase:firebase-messaging:24.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
