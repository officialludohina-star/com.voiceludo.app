plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
}
