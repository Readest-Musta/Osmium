import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is optional. Supply a keystore through environment variables
// (preferred for CI) or through the gitignored local.properties file. The
// keystore and its credentials must NEVER be committed. Debug/test builds do
// not need any of this and must keep working when no keystore is present.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
fun signingValue(property: String, env: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingValue("osmium.keystore.path", "OSMIUM_KEYSTORE_PATH")
val releaseKeystoreFile: File? = releaseKeystorePath?.let {
    val f = File(it)
    if (f.isAbsolute) f else rootProject.file(it)
}
val hasReleaseKeystore = releaseKeystoreFile?.isFile == true

android {
    namespace = "org.osmium.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.osmium.keyboard"
        minSdk = 24
        targetSdk = 35
        // 0.1.18 -> major*10000 + minor*100 + patch.
        versionCode = 118
        versionName = "0.1.18"
    }

    signingConfigs {
        // Only create the config when a keystore actually exists, so the
        // canonical debug build never fails on a machine without secrets.
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = signingValue("osmium.keystore.password", "OSMIUM_KEYSTORE_PASSWORD")
                keyAlias = signingValue("osmium.key.alias", "OSMIUM_KEY_ALIAS")
                keyPassword = signingValue("osmium.key.password", "OSMIUM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 is enabled for the distributed APK; the UniFFI/JNA keep rules
            // in proguard-rules.pro preserve the reflective native bindings.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Without a keystore `assembleRelease` still produces an unsigned
            // APK (usable by F-Droid, which signs builds itself).
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/uniffi")
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // JNA is required by the UniFFI generated Kotlin bindings.
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    testImplementation("junit:junit:4.13.2")
    // JVM unit tests need the real org.json implementation; the android.jar stub throws.
    testImplementation("org.json:json:20240303")
}
