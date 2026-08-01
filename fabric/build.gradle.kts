plugins {
    id("multiloader-loader")
    alias(libs.plugins.loom)
    alias(libs.plugins.kotlinCompose)
}

val modId: String by project

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${libs.versions.parchmentMC.get()}:${libs.versions.parchment.get()}@zip")
    })
    modImplementation(libs.fabricLoader)
    modImplementation(libs.fabricApi)

    modImplementation(libs.flk)

//    modRuntimeOnly("dev.ftb.mods:ftb-quests-fabric:${libs.versions.ftb.get()}")

    modCompileOnly("mezz.jei:jei-1.20.1-fabric:${libs.versions.jei.get()}")
    modRuntimeOnly("mezz.jei:jei-1.20.1-fabric:${libs.versions.jei.get()}")

    modCompileOnly("dev.emi:emi-fabric:${libs.versions.emi.get()}:api")
    modRuntimeOnly("dev.emi:emi-fabric:${libs.versions.emi.get()}")

    modCompileOnly("maven.modrinth:jade:11.13.3+fabric")

    modImplementation("org.appliedenergistics:guideme:${libs.versions.guideme.get()}")
    modImplementation("appeng:appliedenergistics2-fabric:${libs.versions.ae2.get()}")

    val compose = libs.versions.compose.get()
    implementation(project(":kaptor"))
    implementation(libs.compose.runtime)
    include(project(":kaptor"))
    listOf(
        "org.jetbrains.compose.runtime:runtime-desktop:$compose",
        "androidx.collection:collection-jvm:1.4.0",
        "org.jetbrains.kotlinx:atomicfu-jvm:0.23.2",
        "org.antlr:antlr4-runtime:4.9.1",
    ).forEach { include(it) }
}

repositories {
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
        name = "GTCEu Maven"
        url = uri("https://maven.gtceu.com")
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
    mavenCentral()
}

loom {
    val aw = project(":common").file("src/main/resources/${modId}.accesswidener")
    if (aw.exists()) {
        accessWidenerPath.set(aw)
    }
    mixin {
        defaultRefmapName.set("${modId}.refmap.json")
    }
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("runs/client")
        }
        named("server") {
            server()
            configName = "Fabric Server"
            ideConfigGenerated(true)
            runDir("runs/server")
        }
    }
}
