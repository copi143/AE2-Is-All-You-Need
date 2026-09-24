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
    compileOnly(libs.compose.ui.graphics)

    composeRuntime(libs.compose.ui)
    composeRuntime(libs.compose.foundation)
    composeRuntime(libs.compose.foundation.layout)
    composeRuntime(libs.compose.animation)
    composeRuntime(libs.compose.material)
}

val officialUiGraphicsJar = configurations.detachedConfiguration(
    dependencies.create(libs.compose.ui.graphics.get())
)
officialUiGraphicsJar.isTransitive = false

val officialUiDesktopJar = configurations.detachedConfiguration(
    dependencies.create(libs.compose.ui.asProvider().get())
)
officialUiDesktopJar.isTransitive = false

tasks.named<Jar>("jar") {
    description =
        "Produces a Compose desktop runtime jar without skiko, with the skiko-dependent ui-graphics classes replaced by the local implementations."
    from(composeRuntime.map { file ->
        if (file.isDirectory) {
            file
        } else if (file.name.startsWith("ui-desktop-")) {
            zipTree(file).matching { exclude("androidx/compose/ui/graphics/**") }
        } else {
            zipTree(file)
        }
    })
    from(zipTree(officialUiGraphicsJar.singleFile))
    from(zipTree(officialUiDesktopJar.singleFile)) { include("androidx/compose/ui/graphics/**") }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(mapOf("Automatic-Module-Name" to "org.jetbrains.compose.desktop.runtime"))
    }
}
