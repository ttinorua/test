import java.util.Base64
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
        file.inputStream().use { load(it) }
    }
}
/** A build secret from local.properties, or else the environment (a cloud build environment's
 * variables). */
fun buildSecret(name: String): String? =
    (localProperties.getProperty(name) ?: System.getenv(name))?.takeIf { it.isNotBlank() }

// The app's own Anthropic key. FINANCE_APP_ANTHROPIC_API_KEY is checked first because the Claude
// Code cloud environment keeps ANTHROPIC_* variables for its own connection and doesn't pass an
// ANTHROPIC_API_KEY through to builds.
val anthropicApiKey: String =
    (buildSecret("FINANCE_APP_ANTHROPIC_API_KEY") ?: buildSecret("ANTHROPIC_API_KEY"))?.trim() ?: ""

// Enable Banking (Sydbank open-banking sync). The application ID isn't secret — it's sent in the
// clear with every request and is useless without the private key — so the app's registered ID
// is the default. The app expects the private key base64-encoded (of the whole PEM text): either
// supply that as ENABLE_BANKING_PRIVATE_KEY_B64, or paste the .pem file's text as-is into
// ENABLE_BANKING_PRIVATE_KEY and it's encoded here.
val enableBankingApplicationId: String =
    buildSecret("ENABLE_BANKING_APPLICATION_ID")?.trim() ?: "7bf8c383-b4b9-41c5-b126-11cc4f76c1c5"
val enableBankingPrivateKeyB64: String =
    buildSecret("ENABLE_BANKING_PRIVATE_KEY_B64")?.trim()
        ?: buildSecret("ENABLE_BANKING_PRIVATE_KEY")
            // An env var editor may keep the line breaks as literal "\n" text.
            ?.replace("\\n", "\n")
            ?.trim()
            ?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }
        ?: ""

android {
    namespace = "com.financetracker.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.financetracker.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"$anthropicApiKey\"")
        buildConfigField("String", "ENABLE_BANKING_APPLICATION_ID", "\"$enableBankingApplicationId\"")
        buildConfigField("String", "ENABLE_BANKING_PRIVATE_KEY_B64", "\"$enableBankingPrivateKeyB64\"")
    }

    // The app's permanent signing key. Android only installs an update signed with the same key as
    // the installed app, so this must never change — it lives in the build environment
    // (ANDROID_KEYSTORE_B64 = the .p12 file base64-encoded, ANDROID_KEYSTORE_PASSWORD), never in
    // this repository. Without it, release builds come out unsigned.
    val releaseKeystoreB64 = buildSecret("ANDROID_KEYSTORE_B64")
    val releaseKeystorePassword = buildSecret("ANDROID_KEYSTORE_PASSWORD")
    if (releaseKeystoreB64 != null && releaseKeystorePassword != null) {
        signingConfigs {
            create("release") {
                val keystoreFile = layout.buildDirectory.file("signing/release.p12").get().asFile
                keystoreFile.parentFile.mkdirs()
                keystoreFile.writeBytes(Base64.getMimeDecoder().decode(releaseKeystoreB64.trim()))
                storeFile = keystoreFile
                storeType = "pkcs12"
                storePassword = releaseKeystorePassword.trim()
                keyAlias = "financetracker"
                keyPassword = releaseKeystorePassword.trim()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WorkManager: lets the AI categorization backfill keep running (and be observed) across
    // navigation, screen-off, and app backgrounding instead of dying with the ViewModel.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Apache POI for Excel (.xlsx) import/export
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5") {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
    }
    implementation("org.apache.xmlbeans:xmlbeans:5.2.0")

    // Claude API (chat, category suggestions, spending insights)
    implementation("com.anthropic:anthropic-java:2.52.0")

    testImplementation("junit:junit:4.13.2")
    // The real org.json for JVM unit tests (Android's own copy is a stub there).
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
