import com.android.build.api.dsl.Packaging

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.sissi.lab.hprofclipper"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        externalNativeBuild {
            cmake {
                arguments +="-DANDROID_STL=c++_shared"
            }
        }
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
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
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version="3.22.1"
        }
    }

    buildFeatures {
        prefab=true // 需要引用xhook发布库的头文件
    }

    packaging {
        jniLibs{
//            keepDebugSymbols += "**/*.so"
        }
    }
}

dependencies {
    implementation("com.sissi.lab:xhook:1.0")
    implementation("com.sissi.lab:hprofdumper:1.0")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.sissi.lab"
            artifactId = "hprofclipper"
            version = "1.0"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        mavenLocal()

//        maven {
//            name="myrepo"
////            url = uri("${rootProject.projectDir}/build/repository")
//            url = uri("E:\\_tmp")
//        }
    }
}