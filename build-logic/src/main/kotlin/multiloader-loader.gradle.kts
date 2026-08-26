import org.jetbrains.dokka.gradle.DokkaExtension

plugins {
    id("multiloader-common")
    id("org.jetbrains.kotlin.jvm")
}

val modId = project.property("modId") as String

configurations {
    create("commonJava") {
        isCanBeResolved = true
    }
    create("commonKotlin") {
        isCanBeResolved = true
    }
    create("commonResources") {
        isCanBeResolved = true
    }
    create("composeClasses") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
}

dependencies {
    "compileOnly"(project(":common")) {
        capabilities {
            requireCapability("${project.group}:${modId}")
        }
    }
    "commonJava"(project(path = ":common", configuration = "commonJava"))
    "commonKotlin"(project(path = ":common", configuration = "commonKotlin"))
    "commonResources"(project(path = ":common", configuration = "commonResources"))
    // Compose runtime classes are merged straight into the loader jar by :common (no jar-in-jar), and
    // the same resolved files feed the dev classpath.
    "composeClasses"(project(path = ":common", configuration = "composeClasses"))
    // Dev runs use the merged class directory instead of the official jars, because ModLauncher's
    // ModuleClassLoader cannot create modules for jars without an Automatic-Module-Name manifest
    // attribute (official ui-desktop/foundation have none) and silently skips them.
    "runtimeOnly"(project(path = ":common", configuration = "composeClasses"))
}

tasks.named<Jar>("jar") {
    dependsOn(configurations["composeClasses"])
    from(configurations["composeClasses"])
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(configurations["commonJava"])
    source(configurations["commonJava"])
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    dependsOn(configurations["commonKotlin"])
    dependsOn(configurations["commonJava"])
    source(configurations["commonJava"])
    source(configurations["commonKotlin"])
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(configurations["commonResources"])
    from(configurations["commonResources"])
    // common/res is a generated, gitignored source dir: always regenerate it before packaging so
    // build/jar/run* pick up the latest assets even on a fresh clone.
    dependsOn(":common:generateAssets")
    // Compose classes go to the exploded dev resources so runClient sees them exactly like the
    // built jar does (the jar task merges the same configuration).
    dependsOn(configurations["composeClasses"])
    from(configurations["composeClasses"])
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(configurations["commonJava"])
    from(configurations["commonJava"])
    dependsOn(configurations["commonKotlin"])
    from(configurations["commonKotlin"])
    dependsOn(configurations["commonResources"])
    from(configurations["commonResources"])
    // common/res is a generated, gitignored source dir: sourcesJar consumes it via commonResources,
    // so declare the same dependency as processResources to satisfy Gradle's implicit-dependency
    // validation and guarantee up-to-date assets.
    dependsOn(":common:generateAssets")
}

// Use dokka to generate javadoc for both Java and Kotlin sources
// instead of using the builtin javadoc tools. This allows mixing
// Kotlin and Java
tasks.named("dokkaGeneratePublicationJavadoc") {
    dependsOn(configurations["commonJava"])
    dependsOn(configurations["commonKotlin"])
    // Dokka resolves the full compile classpath to type-check sources, which transitively pulls in
    // :common, :kaptor and :averith build outputs. dependsOn the resolved configuration so Gradle
    // declares explicit dependencies on those producer tasks instead of relying on implicit ones.
    dependsOn(configurations["compileClasspath"])
}

configure<DokkaExtension> {
    dokkaSourceSets.named("main") {
        sourceRoots.from(
            configurations["commonJava"],
            configurations["commonKotlin"]
        )
        classpath.from(configurations["compileClasspath"])
    }
}
