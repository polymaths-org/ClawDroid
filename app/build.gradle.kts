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

android {
    namespace = "com.clawdroid.app"
    compileSdk = 36

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

        val githubClientId = localProperties.getProperty("GITHUB_OAUTH_CLIENT_ID") ?: ""
        val githubClientSecret = localProperties.getProperty("GITHUB_OAUTH_CLIENT_SECRET") ?: ""
        val githubToken = localProperties.getProperty("GITHUB_OAUTH_TOKEN") 
            ?: localProperties.getProperty("GITHUB_OAUTH_CLIENT_TOKEN") 
            ?: ""
        val notionClientId = localProperties.getProperty("NOTION_OAUTH_CLIENT_ID") ?: ""
        val notionClientSecret = localProperties.getProperty("NOTION_OAUTH_CLIENT_SECRET") ?: ""
        val spotifyClientId = localProperties.getProperty("SPOTIFY_OAUTH_CLIENT_ID") ?: ""
        val spotifyClientSecret = localProperties.getProperty("SPOTIFY_OAUTH_CLIENT_SECRET") ?: ""

        buildConfigField("String", "LLM_BASE_URL", llmBaseUrl.asBuildConfigString())
        buildConfigField("String", "LLM_MODEL", llmModel.asBuildConfigString())
        buildConfigField("String", "LLM_API_KEY", llmApiKey.asBuildConfigString())
        buildConfigField("String", "LLM_PROVIDER", llmProvider.asBuildConfigString())

        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", githubClientId.asBuildConfigString())
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_SECRET", githubClientSecret.asBuildConfigString())
        buildConfigField("String", "GITHUB_OAUTH_TOKEN", githubToken.asBuildConfigString())
        buildConfigField("String", "NOTION_OAUTH_CLIENT_ID", notionClientId.asBuildConfigString())
        buildConfigField("String", "NOTION_OAUTH_CLIENT_SECRET", notionClientSecret.asBuildConfigString())
        buildConfigField("String", "SPOTIFY_OAUTH_CLIENT_ID", spotifyClientId.asBuildConfigString())
        buildConfigField("String", "SPOTIFY_OAUTH_CLIENT_SECRET", spotifyClientSecret.asBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

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
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

