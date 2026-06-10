import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Fail fast if local.properties is missing — credentials are required to build
val localPropsFile = rootProject.file("local.properties")
if (!localPropsFile.exists()) {
    throw GradleException(
        "\n\n[YHWH] local.properties not found at ${localPropsFile.absolutePath}.\n" +
        "Create it with the following keys before building:\n" +
        "  SUPABASE_URL=https://<project-ref>.supabase.co\n" +
        "  SUPABASE_ANON_KEY=<anon-key>\n" +
        "  GOOGLE_SERVER_CLIENT_ID=<web-client-id>.apps.googleusercontent.com\n"
    )
}
val localProps = Properties().apply { localPropsFile.inputStream().use { load(it) } }

fun requireLocalProp(key: String): String {
    val value = localProps[key]?.toString()
    if (value.isNullOrBlank()) {
        throw GradleException(
            "\n\n[YHWH] Missing required property '$key' in local.properties.\n" +
            "Add the following line and rebuild:\n  $key=<value>\n"
        )
    }
    return value
}

android {
    namespace = "com.madmaxlgndklr.yhwh"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.madmaxlgndklr.yhwh"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.12"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL",
            "\"${requireLocalProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",
            "\"${requireLocalProp("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID",
            "\"${requireLocalProp("GOOGLE_SERVER_CLIENT_ID")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    // Supabase
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.compose.auth)
    implementation(libs.ktor.android)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(composeBom)
}
