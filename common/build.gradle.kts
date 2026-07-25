plugins {
    id("multiloader-common")
    alias(libs.plugins.moddev)
    alias(libs.plugins.kotlinCompose)
}

neoForge {
    neoFormVersion = libs.versions.neoForm
    // Automatically enable AccessTransformers if the file exists
    val at = file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    parchment {
        minecraftVersion = libs.versions.parchmentMC
        mappingsVersion = libs.versions.parchment
    }
}

dependencies {
    compileOnly(libs.mixin)
    compileOnly(libs.compose.runtime)

    modCompileOnly("mezz.jei:jei-1.20.1-forge:${libs.versions.jei.get()}")
    modCompileOnly("dev.emi:emi-xplat-mojmap:${libs.versions.emi.get()}:api")

    modCompileOnly("org.appliedenergistics:guideme:${libs.versions.guideme.get()}")
    modCompileOnly("appeng:appliedenergistics2-forge:${libs.versions.ae2.get()}")
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

configurations {
    create("commonJava") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("commonKotlin") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("commonResources") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
}

sourceSets.create("resgen") {
    compileClasspath += sourceSets.main.get().output
    kotlin.srcDir("resgen")
}.let {
    tasks.register<JavaExec>("generateAssets") {
        description = "Generates assets for the mod using the resgen source set."
        dependsOn(it.compileClasspath)
        dependsOn(tasks.named("classes"))
        classpath = it.runtimeClasspath
        mainClass.set("allyouneed.resgen.MainKt")
        workingDir = rootProject.layout.projectDirectory.asFile
    }
    tasks.jar {
        dependsOn(it)
    }
    dependencies {
        "resgenImplementation"("com.github.ajalt.colormath:colormath:3.6.1")
        "resgenImplementation"("com.google.code.gson:gson:2.10.1")
        "resgenImplementation"(libs.compose.runtime)
    }
    sourceSets.main {
        resources.srcDir("res")
    }
}

artifacts {
    add("commonJava", sourceSets.main.get().java.sourceDirectories.singleFile)
    add("commonKotlin", sourceSets.main.get().kotlin.sourceDirectories.filter { !it.name.endsWith("java") }.singleFile)
    sourceSets.main.get().resources.sourceDirectories.forEach { resourceDir ->
        add("commonResources", resourceDir)
    }
}
