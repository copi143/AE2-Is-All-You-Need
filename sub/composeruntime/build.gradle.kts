plugins {
    id("multiloader-base")
}

version = "0.1.0+compose.${libs.versions.compose.get().replace('-', '.')}"

val composeRuntime = configurations.create("composeRuntime") {
    isCanBeResolved = true
    isCanBeConsumed = false
    exclude(group = "org.jetbrains.skiko")
    exclude(group = "org.jetbrains.compose.ui", module = "ui-graphics-desktop")
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
}

dependencies {
    composeRuntime(libs.compose.ui)
    composeRuntime(libs.compose.foundation)
    composeRuntime(libs.compose.foundation.layout)
    composeRuntime(libs.compose.animation)
    composeRuntime(libs.compose.material)
    composeRuntime(project(":graphicsrepl"))
}

val unpackComposeClasses = tasks.register<Sync>("unpackComposeClasses") {
    dependsOn(":graphicsrepl:jar", composeRuntime)
    from(composeRuntime.map { file ->
        if (file.isDirectory) {
            file
        } else if (file.name.startsWith("ui-desktop-")) {
            zipTree(file).matching { exclude("androidx/compose/ui/graphics/**") }
        } else {
            zipTree(file)
        }
    })
    into(layout.buildDirectory.dir("composeClasses"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Jar>("jar") {
    dependsOn(unpackComposeClasses)
    from(layout.buildDirectory.dir("composeClasses"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(mapOf("Automatic-Module-Name" to "org.jetbrains.compose.desktop.runtime"))
    }
}
