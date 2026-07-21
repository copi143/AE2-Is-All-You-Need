import org.gradle.internal.extensions.stdlib.capitalized

plugins {
    id("multiloader-loader")
    alias(libs.plugins.moddev)
    alias(libs.plugins.kotlinCompose)
}

val modId: String by project

mixin {
    add(sourceSets.main.get(), "${modId}.refmap.json")
    config("${modId}.mixins.json")
    config("${modId}.forge.mixins.json")
}
tasks.jar {
    manifest {
        attributes["MixinConfigs"] = "${modId}.mixins.json,${modId}.forge.mixins.json"
    }
}

neoForge {
    version = libs.versions.forge
    // Automatically enable neoforge AccessTransformers if the file exists
    val at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    parchment {
        minecraftVersion = libs.versions.parchmentMC
        mappingsVersion = libs.versions.parchment
    }
    runs {
        configureEach {
            systemProperty("forge.enabledGameTestNamespaces", modId)
            ideName = "Forge ${name.capitalized()} (${project.path})" // Unify the run config names with fabric
        }
        register("client") {
            client()
        }
        register("data") {
            data()
        }
        register("server") {
            server()
        }
    }
    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources { srcDir("src/generated/resources") }

dependencies {
    modImplementation(libs.kff)
    annotationProcessor(variantOf(libs.mixin) { classifier("processor") })
    implementation(libs.compose.runtime)

    modCompileOnly("mezz.jei:jei-1.20.1-forge:${libs.versions.jei.get()}")
    modRuntimeOnly("mezz.jei:jei-1.20.1-forge:${libs.versions.jei.get()}")

    modCompileOnly("dev.emi:emi-forge:${libs.versions.emi.get()}:api")
    modRuntimeOnly("dev.emi:emi-forge:${libs.versions.emi.get()}")

    modImplementation("org.appliedenergistics:guideme:${libs.versions.guideme.get()}")
    modImplementation("appeng:appliedenergistics2-forge:${libs.versions.ae2.get()}")

    modCompileOnly("com.gregtechceu.gtceu:gtceu-${libs.versions.minecraft.get()}:${libs.versions.gt.get()}")
    modRuntimeOnly("com.gregtechceu.gtceu:gtceu-${libs.versions.minecraft.get()}:${libs.versions.gt.get()}")

    modCompileOnly("mekanism:Mekanism:${libs.versions.mek.get()}:api")
    modRuntimeOnly("mekanism:Mekanism:${libs.versions.mek.get()}")
    modRuntimeOnly("mekanism:Mekanism:${libs.versions.mek.get()}:additions")
    modRuntimeOnly("mekanism:Mekanism:${libs.versions.mek.get()}:generators")
    modRuntimeOnly("mekanism:Mekanism:${libs.versions.mek.get()}:tools")
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
    maven {
        name = "JetBrains Compose"
        url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    maven {
        name = "Google Android"
        url = uri("https://dl.google.com/dl/android/maven2/")
    }
    mavenCentral()
}
