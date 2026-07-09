import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.inputStream().use(::load)
    }
}

val releaseSigningProperties = Properties().apply {
    val file = rootProject.file("signing/release-signing.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun releaseSigningProperty(localName: String, envName: String = localName): String? {
    return releaseSigningProperties.getProperty(localName)
        ?: localProperties.getProperty(localName)
        ?: System.getenv(envName)
}

val releaseStoreFile = releaseSigningProperty("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningProperty("storePassword", "RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningProperty("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningProperty("keyPassword", "RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.clawdroid.app"
    compileSdk = 36

    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    defaultConfig {
        applicationId = "com.clawdroid.app"
        minSdk = 26
        targetSdk = 28
        versionCode = 2
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val llmBaseUrl = localProperties.getProperty("LLM_BASE_URL")
            ?: "https://api.siliconflow.com/v1"
        val llmModel = localProperties.getProperty("LLM_MODEL")
            ?: "moonshotai/Kimi-K2.6"
        val llmApiKey = localProperties.getProperty("LLM_API_KEY") ?: ""
        val llmProvider = localProperties.getProperty("LLM_PROVIDER") ?: "siliconflow"
        val openaiRealtimeApiKey = localProperties.getProperty("OPENAI_REALTIME_API_KEY") ?: ""

        val githubClientId = localProperties.getProperty("GITHUB_OAUTH_CLIENT_ID") ?: ""
        val githubClientSecret = localProperties.getProperty("GITHUB_OAUTH_CLIENT_SECRET") ?: ""
        val githubToken = localProperties.getProperty("GITHUB_OAUTH_TOKEN") 
            ?: localProperties.getProperty("GITHUB_OAUTH_CLIENT_TOKEN") 
            ?: ""
        val notionClientId = localProperties.getProperty("NOTION_OAUTH_CLIENT_ID") ?: ""
        val notionClientSecret = localProperties.getProperty("NOTION_OAUTH_CLIENT_SECRET") ?: ""
        val spotifyClientId = localProperties.getProperty("SPOTIFY_OAUTH_CLIENT_ID") ?: ""
        val spotifyClientSecret = localProperties.getProperty("SPOTIFY_OAUTH_CLIENT_SECRET") ?: ""

        // Google OAuth (server-auth-code flow). The WEB client carries the secret used for
        // token exchange; the Android client authenticates on-device via package + SHA-1.
        // Prefer local.properties/.env overrides, then fall back to the web client JSON in root.
        val googleClientId = localProperties.getProperty("GOOGLE_OAUTH_CLIENT_ID")
            ?: readGoogleWebCredential("client_id")
            ?: "430112870946-niqg2aadqk31uhmitaqdapp3mt17bfu9.apps.googleusercontent.com"
        val googleClientSecret = localProperties.getProperty("GOOGLE_OAUTH_CLIENT_SECRET")
            ?: readGoogleWebCredential("client_secret")
            ?: ""

        buildConfigField("String", "LLM_BASE_URL", llmBaseUrl.asBuildConfigString())
        buildConfigField("String", "LLM_MODEL", llmModel.asBuildConfigString())
        buildConfigField("String", "LLM_API_KEY", llmApiKey.asBuildConfigString())
        buildConfigField("String", "LLM_PROVIDER", llmProvider.asBuildConfigString())
        buildConfigField("String", "OPENAI_REALTIME_API_KEY", openaiRealtimeApiKey.asBuildConfigString())

        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", githubClientId.asBuildConfigString())
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_SECRET", githubClientSecret.asBuildConfigString())
        buildConfigField("String", "GITHUB_OAUTH_TOKEN", githubToken.asBuildConfigString())
        buildConfigField("String", "NOTION_OAUTH_CLIENT_ID", notionClientId.asBuildConfigString())
        buildConfigField("String", "NOTION_OAUTH_CLIENT_SECRET", notionClientSecret.asBuildConfigString())
        buildConfigField("String", "SPOTIFY_OAUTH_CLIENT_ID", spotifyClientId.asBuildConfigString())
        buildConfigField("String", "SPOTIFY_OAUTH_CLIENT_SECRET", spotifyClientSecret.asBuildConfigString())
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", googleClientId.asBuildConfigString())
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_SECRET", googleClientSecret.asBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun readGoogleWebCredential(key: String): String? {
    return try {
        val webJson = rootProject.projectDir.listFiles()
            ?.firstOrNull {
                it.isFile &&
                    it.name.startsWith("client_secret_") &&
                    it.name.contains("-niqg2") &&
                    it.name.endsWith(".json")
            }
        if (webJson != null && webJson.exists()) {
            val parsed = groovy.json.JsonSlurper().parse(webJson) as Map<*, *>
            (parsed["web"] as? Map<*, *>)?.get(key) as? String
        } else null
    } catch (_: Exception) {
        null
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0")
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
