//import com.android.build.api.dsl.Packaging

plugins {
//    alias(libs.plugins.android.library)
//    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.android.library")
//    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.sissi.lab.hprofclipper"
    compileSdk = 34
    ndkVersion = "23.1.7779620" // koom使用的版本，它的agp7.1.0
//    ndkVersion = "25.1.8937393"
//    ndkVersion = "26.1.10909125" // agp8.4.1默认的ndk版本，agp8.4.1是本项目首次正常跑起来时（通过prefab发布和引用相关组件）默认的版本

    defaultConfig {
//        minSdk = 18 // AGP 7.1引入了prefab功能，18不支持prefab。
        minSdk = 18 // 经实测至少24才可正常使用prefab，否则找不到通过prefab发布的xhook
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
//    kotlinOptions {
//        jvmTarget = "1.8"
//    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version="3.22.1"
        }
    }

    buildFeatures {
        prefab=true // 需要引用xhook发布库的头文件
    }

//    packaging {
//        jniLibs{
//            keepDebugSymbols += "**/*.so"
//        }
//    }
}

dependencies {
    implementation("com.sissi.lab:xhook:1.0")
    implementation("com.sissi.lab:hprofdumper:1.0")
}

publishing {
    publications {
        register<MavenPublication>("hprofclipper") {
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