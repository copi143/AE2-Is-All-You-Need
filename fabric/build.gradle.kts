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

    // The common GT bridge (allyouneed.gtceu) is recompiled into this module's sources, so the plain
    // GTCEu jar (mojmap, unremapped) must be on the compile classpath. Never a runtime dependency.
    // IMachineBlockEntity extends IForgeBlockEntity; fabric has no Forge classes, so pull the
    // Forge universal jar (compile-only) to resolve the hierarchy. The classifier artifact ships the
    // net.minecraftforge.* classes without the userdev zip.
    compileOnly("net.minecraftforge:forge:${libs.versions.forge.get()}:universal")
    modCompileOnly(libs.gtceu)

    implementation(project(":kaptor"))
    implementation(libs.compose.runtime)
    include(project(":kaptor"))
    // Compose UI classes are merged straight into the mod jar via :common's composeClasses, not include.
    include("org.antlr:antlr4-runtime:4.9.1")
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
