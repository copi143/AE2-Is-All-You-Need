plugins {
    id("multiloader-loader")
    alias(libs.plugins.loom)
    alias(libs.plugins.kotlin.compose)
}

val modId = project.property("modId") as String

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${libs.versions.parchmentMC.get()}:${libs.versions.parchment.get()}@zip")
    })
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    modImplementation(libs.flk)

    implementation(libs.compose.runtime)

    compileOnly(project(":transformer"))
    listOf(
        include(project(path = ":transformer", configuration = "withInject")),
        include(project(":kaptor")),
        include(project(":averith")),
        include(project(":indexing")),
        include(project(":composeruntime")),
        include(project(":msdftext")),
        include(libs.antlr.runtime),
        include(libs.ojalgo),
    ).forEach { if (it != null) implementation(it) }
    modImplementation(libs.jetbrains.markdown)
    modImplementation(libs.netty.codec.http)
    include(libs.jetbrains.markdown) {
        isTransitive = false
    }
    include(libs.netty.codec.http) {
        isTransitive = false
    }

    modImplementation(libs.jei.fabric)
    modImplementation(libs.emi.fabric)
    modImplementation(libs.jade.fabric)

    modImplementation(libs.energy)
    modImplementation(libs.guideme)
    modImplementation(libs.ae2.fabric)

    // The common GT bridge (allyouneed.gtceu) is recompiled into this module's sources, so the plain
    // GTCEu jar (mojmap, unremapped) must be on the compile classpath. Never a runtime dependency.
    // IMachineBlockEntity extends IForgeBlockEntity; fabric has no Forge classes, so pull the
    // Forge universal jar (compile-only) to resolve the hierarchy. The classifier artifact ships the
    // net.minecraftforge.* classes without the userdev zip.
    compileOnly(variantOf(libs.forge) { classifier("universal") })
    modCompileOnly(libs.gtceu)
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
