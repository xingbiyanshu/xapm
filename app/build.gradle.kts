plugins {
//    alias(libs.plugins.android.application)
//    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sissi.lib.xapm"
    compileSdk = 34

//    ndkVersion = "23.1.7779620"
//    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.sissi.lib.xapm"
        minSdk = 19
//        targetSdk = 30
        versionCode = 1
        versionName = "1.0"

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
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packagingOptions {
        jniLibs {
            pickFirsts +="lib/*/libc++_shared.so"
            pickFirsts +="lib/*/liblog.so"
        }
    }
}

//dependencies {
//
//    implementation(libs.androidx.core.ktx)
//    implementation(libs.androidx.appcompat)
//    implementation(libs.material)
//    implementation(libs.androidx.activity)
//    implementation(libs.androidx.constraintlayout)
////    implementation("com.sissi.lab:hprofdumper:1.0")
//    implementation("com.sissi.lab:hprofclipper:1.0")
//}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.0")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.activity:activity:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.sissi.lab:hprofclipper:1.0")
}

