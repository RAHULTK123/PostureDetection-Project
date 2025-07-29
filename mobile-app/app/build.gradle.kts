plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.posture_detection_app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.posture_detection_app"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    // It's generally better to put the repositories block outside the android block,
    // like shown above the 'android' block.
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0") // Consider update
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation(platform("com.google.firebase:firebase-bom:33.9.0")) // Check latest BoM
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.code.gson:gson:2.10") // Keep if used
    implementation("com.google.firebase:firebase-auth:23.2.0") // Check latest Auth

    // --- Added MPAndroidChart Dependency using Kotlin DSL syntax ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // <-- Use ("...") syntax

}