plugins {
    id("multiloader-loader")
    alias(libs.plugins.loom)
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

    modCompileOnly("mezz.jei:jei-1.20.1-fabric:${libs.versions.jei.get()}")
    modRuntimeOnly("mezz.jei:jei-1.20.1-fabric:${libs.versions.jei.get()}")

    modCompileOnly("dev.emi:emi-fabric:${libs.versions.emi.get()}:api")
    modRuntimeOnly("dev.emi:emi-fabric:${libs.versions.emi.get()}")

    modImplementation("org.appliedenergistics:guideme:${libs.versions.guideme.get()}")
    modImplementation("appeng:appliedenergistics2-fabric:${libs.versions.ae2.get()}")
}

repositories {
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
