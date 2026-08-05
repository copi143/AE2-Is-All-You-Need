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

    modImplementation(libs.jei.fabric)
    modImplementation(libs.emi.fabric)
    modImplementation(libs.jade.fabric)

    modImplementation(libs.guideme)
    modImplementation(libs.ae2.fabric)

    // The common GT bridge (allyouneed.gt) is recompiled into this module's sources, so the plain
    // GTCEu jar (mojmap, unremapped) must be on the compile classpath. Never a runtime dependency.
    // IMachineBlockEntity extends IForgeBlockEntity; fabric has no Forge classes, so pull the
    // Forge universal jar (compile-only) to resolve the hierarchy. The classifier artifact ships the
    // net.minecraftforge.* classes without the userdev zip.
    compileOnly(libs.forge)
    modCompileOnly(libs.gtceu)

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
        name = "Forge"
        url = uri("https://maven.minecraftforge.net")
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
    maven {
        name = "FirstDarkDev Maven"
        url = uri("https://maven.firstdark.dev/snapshots")
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
