plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sissi.apm.proc.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sissi.apm.proc.demo"
        minSdk = 24
        targetSdk = 34
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
}

dependencies {

//    implementation("androidx.core:core-ktx:1.13.1")
//    implementation("androidx.appcompat:appcompat:1.7.0")
//    implementation("com.google.android.material:material:1.12.0")
//    implementation("androidx.activity:activity:1.9.2")

    implementation("androidx.core:core-ktx:1.10.1")
//    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.0"))
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
//    implementation("androidx.activity:activity:1.8.0")
//    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation(project(":proc"))
}