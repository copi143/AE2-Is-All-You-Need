plugins {
    id("multiloader-loader")
    alias(libs.plugins.loom)
    alias(libs.plugins.kotlin.compose)
}

val modId = project.property("modId") as String

sourceSets.main {
    java.srcDir("src")
    kotlin.srcDir("src")
    resources.srcDir("resources")
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${libs.versions.parchmentMC.get()}:${libs.versions.parchment.get()}@zip")
    })
    modImplementation(libs.fabricLoader)
    modImplementation(libs.fabricApi)

    modImplementation(libs.flk)

    include(libs.ojalgo)
    include(libs.jetbrains.markdown)
    include(libs.netty.codec.http) {
        isTransitive = false
    }

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

    implementation(project(":transformer"))
    include(project(":transformer"))
    include(project(":kaptor"))
    include(project(":averith"))
    include("org.antlr:antlr4-runtime:4.9.1")
}

loom {
    val aw = project(":common").file("resources/${modId}.aw")
    if (aw.exists()) {
        accessWidenerPath.set(aw)
    }
    mixin {
        useLegacyMixinAp = true
        defaultRefmapName.set("${modId}.refmap.json")
    }
    runs {
        named("client") {
            client()
            displayName = "Fabric Client"
            generateRunConfig = true
            runDirectory.dir("runs/client")
        }
        named("server") {
            server()
            displayName = "Fabric Server"
            generateRunConfig = true
            runDirectory.dir("runs/server")
        }
    }
}
