plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.rostros"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.rostros"
        minSdk = 29
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    //Agregando las dependencias de MLkit, asi como la version de la camara
    implementation ("com.google.mlkit:face-detection:16.1.5")
    implementation ("com.google.android.gms:play-services-mlkit-face-detection:16.1.5")
    implementation ("androidx.camera:camera-core:1.3.0")

    val version = "1.4.1"
    //Camara
    implementation("androidx.camera:camera-core:${version}")
    implementation("androidx.camera:camera-camera2:${version}")
    implementation("androidx.camera:camera-lifecycle:${version}")
    implementation("androidx.camera:camera-view:${version}")
}
