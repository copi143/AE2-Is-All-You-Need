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
    api(project(":kaptor"))
    api(libs.kotlinx.coroutines.core)

//    modCompileOnly("dev.ftb.mods:ftb-quests:${libs.versions.ftb.get()}")

    modCompileOnly(libs.jei.forge)
    modCompileOnly("dev.emi:emi-xplat-mojmap:${libs.versions.emi.get()}:api")

    modCompileOnly(libs.guideme)
    modCompileOnly(libs.ae2.forge)

    // The moddev-generated minecraft jar does not carry the Forge extension interfaces
    // (net.minecraftforge.common.extensions.*) that GTCEu's IMachineBlockEntity extends.
    compileOnly("net.minecraftforge:forge:${libs.versions.forge.get()}:universal")
    modCompileOnly(libs.gtceu)

    // Mixin's IMixinConfigPlugin declares org.objectweb.asm.tree.ClassNode (and the shaded
    // mixin jar does not bundle ASM), so the plugin needs it on the compile classpath.
    compileOnly("org.ow2.asm:asm-tree:9.8")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.5")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation(libs.compose.runtime) // required by compose compiler plugin on test source set
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
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
    maven {
        name = "FirstDarkDev Maven"
        url = uri("https://maven.firstdark.dev/snapshots")
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
        javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
        workingDir = rootProject.layout.projectDirectory.asFile
    }
    tasks.jar {
        dependsOn(it.classesTaskName)
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
    add("commonJava", sourceSets.main.get().java.sourceDirectories.first())
    add("commonKotlin", sourceSets.main.get().kotlin.sourceDirectories.filter { !it.name.endsWith("java") }.first())
    sourceSets.main.get().resources.sourceDirectories.forEach { resourceDir ->
        add("commonResources", resourceDir)
    }
}
