pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven {
            // RetroFuturaGradle
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroup("com.gtnewhorizons.retrofuturagradle")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

plugins {
    // Automatic toolchain provisioning
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.4.0"
}

dependencyResolutionManagement { 
    versionCatalogs { 
        create("libs") {
            version("kotlinVersion", settings.extra.properties["kotlin_version"].toString())
        }
    }
}

// Due to an IntelliJ bug, this has to be done
// rootProject.name = archives_base_name
rootProject.name = rootProject.projectDir.name
