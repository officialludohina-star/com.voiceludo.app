plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Firebase (Google/Facebook login) — app/google-services.json isi plugin se padhi jati hai
    id("com.google.gms.google-services")
}

android {
    namespace = "com.voiceludo.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.voiceludo.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // FIXED debug key: GitHub Actions har build par nayi debug key banata hai, jis se SHA-1
    // badal jata aur Google login "error 10"/"no credentials" dene lagta. Ab har build isi
    // app/debug.keystore se sign hota hai, to SHA-1 hamesha ek hi rehta hai (Firebase mein
    // iska SHA-1 add hona zaroori hai). Yeh sirf DEBUG key hai (public), release key nahi.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("io.coil-kt:coil-compose:2.6.0")
    // GIF animate karne ke liye zaroori hai — warna dice-roll-animation.gif sirf ek static
    // frame dikhata hai, chalta nahi (Coil GIF frames ko by default decode nahi karta).
    implementation("io.coil-kt:coil-gif:2.6.0")
    // Admin panel se aane wali SVG files dikhane ke liye
    implementation("io.coil-kt:coil-svg:2.6.0")
    // Bekend (Go WebSocket server) se connect karne aur EmailJS ko real OTP email
    // bhejne ke liye — dono hi plain HTTP/WS calls hain, isliye halki si OkHttp
    // library kaafi hai (koi bhaari Retrofit/Ktor waghera nahi chahiye).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Admin panel se aane wali .svga animation files chalane ke liye
    implementation("com.github.yyued:SVGAPlayer-Android:2.6.1")

    // ---- Google login (Firebase Authentication + Credential Manager) ----
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
