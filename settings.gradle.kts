enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "AE2-Is-All-You-Need"

pluginManagement {
    // includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net")
        }
    }
    plugins {
        kotlin("kapt") version "2.4.10"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include("common")
include("fabric")
include("forge")
include("graphicsrepl")
include("kaptor")
include("averith")
