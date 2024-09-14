pluginManagement {
    repositories {
//        maven{
//            url=uri("https://maven.aliyun.com/repository/gradle-plugin")
//        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "xapm"
include(":hprof:hprofdumper")
include(":hprof:hprofclipper")
include(":hprof:demo")
include(":oom:procres")
