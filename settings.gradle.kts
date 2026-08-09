enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "AE2-Is-All-You-Need"

pluginManagement {
    // includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "Fabric"
                    url = uri("https://maven.fabricmc.net")
                }
            }
            filter {
                includeGroup("net.fabricmc")
                includeGroup("fabric-loom")
            }
        }
    }
    plugins {
        kotlin("kapt") version "2.4.10"
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        register("libs") {
            from(files("libs.versions.toml"))
        }
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
