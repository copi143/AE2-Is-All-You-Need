import org.jetbrains.dokka.gradle.DokkaExtension

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

plugins {
    `java-library`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.dokka-javadoc")
}

val modId = project.property("modId") as String
val modName = project.property("modName") as String
val modAuthor = project.property("modAuthor") as String
val license = project.property("license") as String
val credits = project.property("credits") as String

base {
    archivesName.set("${modId}-${project.name}-${libs.versions.minecraft.get()}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

// GTCEu bundles its real dependencies (LDLib, Registrate, configuration) as nested jars under
// META-INF/jarjar/ inside the gtceu jar and publishes an empty POM, so the classes are invisible to
// the compiler. Extract them so the common GT bridge (allyouneed.gt) can compile against them. This
// is compile-only: at runtime Forge's jarjar extraction provides them, and fabric never loads GT.
configurations {
    create("gtceujar") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
}

dependencies {
    "gtceujar"(libs.gtceu)
}

val extractGtJarjar = tasks.register<Sync>("extractGtJarjar") {
    dependsOn(configurations["gtceujar"])
    from(configurations["gtceujar"].map { zipTree(it).matching { include("META-INF/jarjar/*.jar") } })
    into(layout.buildDirectory.dir("gtjarjar"))
    // Strip META-INF/jarjar/ prefix by using flat name mapping
    filesMatching("META-INF/jarjar/*.jar") {
        relativePath = RelativePath(true, name)
    }
}

dependencies {
    compileOnly(files(extractGtJarjar.map { it.outputs.files.asFileTree.files }))
}

// Declare capabilities on the outgoing configurations.
// Read more about capabilities here: https://docs.gradle.org/current/userguide/component_capabilities.html#sec:declaring-additional-capabilities-for-a-local-component
listOf("apiElements", "runtimeElements", "sourcesElements", "javadocElements").forEach { variant ->
    configurations[variant].outgoing {
        capability("${project.group}:${base.archivesName.get()}:${project.version}")
        capability(
            "${project.group}:${modId}-${project.name}-${
                libs.versions.minecraft.get()
            }:${project.version}"
        )
        capability("${project.group}:${modId}:${project.version}")
    }
    publishing.publications.configureEach {
        if (this is MavenPublication) {
            suppressPomMetadataWarningsFor(variant)
        }
    }
}

tasks.named<Jar>("sourcesJar") {
    if (project.path == ":common") {
        dependsOn("generateAssets")
    } else {
        dependsOn(":common:generateAssets")
    }
    from(rootProject.file("LICENSE"))
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE"))

    manifest {
        attributes(
            mapOf(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Specification-Version" to archiveVersion,
                "Implementation-Title" to project.name,
                "Implementation-Version" to archiveVersion,
                "Implementation-Vendor" to modAuthor,
                "Built-On-Minecraft" to libs.versions.minecraft.get()
            )
        )
    }
}

configure<DokkaExtension> {
    dokkaSourceSets.configureEach {
        skipDeprecated.set(false)
        reportUndocumented.set(false)
        sourceRoots.from(project.the<JavaPluginExtension>().sourceSets["main"].allSource)
    }
}

tasks.named("dokkaGeneratePublicationJavadoc") {
    if (project.path == ":common") {
        dependsOn("generateAssets")
    } else {
        dependsOn(":common:generateAssets")
    }
}

// Prevent the default javadoc task from running, as Dokka is
// responsible for generating the docs now
tasks.named<Javadoc>("javadoc") {
    isEnabled = false
}

// Make the javadoc jar take in the Dokka output
tasks.named<Jar>("javadocJar") {
    dependsOn(tasks.named("dokkaGeneratePublicationJavadoc"))
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
}

tasks.named<ProcessResources>("processResources") {
    val expandProps = mapOf(
        "version" to project.version,
        "group" to project.group,
        "minecraft_version" to libs.versions.minecraft.get(),
        "minecraft_version_range" to libs.versions.minecraftRange.get(),
        "fabric_version" to libs.versions.fabricApi.get(),
        "fabric_loader_version" to libs.versions.fabricLoader.get(),
        "flk_version" to libs.versions.flk.get(),
        "mod_name" to modName,
        "mod_author" to modAuthor,
        "mod_id" to modId,
        "license" to license,
        "description" to project.description,
        "forge_version" to libs.versions.forge.get(),
        "forge_range" to libs.versions.forgeRange.get(),
        "kff_version" to libs.versions.kff.get(),
        "kff_version_range" to libs.versions.kffRange.get(),
        "credits" to credits,
        "java_version" to libs.versions.java.get(),
    )

    filesMatching(listOf("pack.mcmeta", "fabric.mod.json", "META-INF/*mods.toml", "*.mixins.json")) {
        expand(expandProps)
    }
    inputs.properties(expandProps)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
    repositories {
        val mavenUrl = System.getenv("local_maven_url")
        if (!mavenUrl.isNullOrEmpty()) {
            maven {
                url = uri(mavenUrl)
            }
        }
    }
}
