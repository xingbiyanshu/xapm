plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.sissi.lab.hprofdumper"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        externalNativeBuild {
            cmake {
//                arguments +="-DANDROID_STL=c++_shared"
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
            version="3.22.1"  // 选用了较旧的版本。若要升级该版本需注意Gradle,AGP,ndk,jdk,cmake等工具之间有版本适配关系。
        }
    }

// 暂时不需要启用prefab（不需要对外提供c/c++头文件，也不需要引用其他发布库的头文件）
//    buildFeatures {
////        prefab=true
//        prefabPublishing = true
//    }
//    prefab {
//        create("hprofdumper"){
//            headers="src/main/cpp/include"
//        }
//    }
}

dependencies {
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.sissi.lab"
            artifactId = "hprofdumper"
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