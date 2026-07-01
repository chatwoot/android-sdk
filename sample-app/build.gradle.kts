import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Chatwoot credentials for manual testing live in local.properties (not committed):
//   chatwoot.baseUrl=https://app.chatwoot.com
//   chatwoot.websiteToken=<website inbox token>
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.chatwoot.android.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chatwoot.android.sample"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "CHATWOOT_BASE_URL",
            "\"${localProps.getProperty("chatwoot.baseUrl", "https://app.chatwoot.com")}\"",
        )
        buildConfigField(
            "String",
            "CHATWOOT_WEBSITE_TOKEN",
            "\"${localProps.getProperty("chatwoot.websiteToken", "")}\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":sdk"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
}
