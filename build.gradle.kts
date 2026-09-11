plugins {
    id("gitversion")
    // see https://fabricmc.net/develop/ for new versions
    alias(libs.plugins.loom) apply false
    // see https://projects.neoforged.net/neoforged/moddevgradle for new versions
    alias(libs.plugins.moddev) apply false
}

allprojects {
    project.version = rootProject.version

    // Shared repositories for every subproject (declared once here instead of per-module).
    repositories {
        mavenCentral()
        exclusiveContent {
            forRepositories(maven {
                name = "ParchmentMC"
                url = uri("https://maven.parchmentmc.org/")
            })
            filter { includeGroup("org.parchmentmc.data") }
        }
        // Forge universal jar (net.minecraftforge:forge) is needed compile-only by common/fabric for
        // GTCEu's IForgeBlockEntity hierarchy; keep this repo unfiltered so any group can resolve.
        maven {
            name = "Forge Releases"
            url = uri("https://maven.minecraftforge.net")
        }
        maven {
            name = "BlameJared"
            url = uri("https://maven.blamejared.com")
        }
        maven {
            name = "kotlinforforge"
            url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        }
        maven {
            name = "GTCEu Maven"
            url = uri("https://maven.gtceu.com")
        }
        maven {
            name = "Modrinth"
            url = uri("https://api.modrinth.com/maven")
        }
        maven {
            name = "TerraformersMC"
            url = uri("https://maven.terraformersmc.com/")
        }
        maven {
            name = "ModMaven"
            url = uri("https://modmaven.dev/")
        }
        maven {
            name = "JetBrains Compose"
            url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        }
        maven {
            name = "Google Android"
            url = uri("https://dl.google.com/dl/android/maven2/")
        }
        maven {
            name = "IzzelAliz Maven"
            url = uri("https://maven.izzel.io/releases/")
        }
        maven {
            name = "Architectury Maven"
            url = uri("https://maven.architectury.dev/")
        }
        maven {
            name = "FTB Maven"
            url = uri("https://maven.ftb.dev/releases/")
        }
        maven {
            name = "FirstDarkDev Maven"
            url = uri("https://maven.firstdark.dev/snapshots")
        }
    }
}

println("version: $version")
