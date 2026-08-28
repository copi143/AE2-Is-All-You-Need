plugins {
    id("multiloader-base")
}

val compose = libs.versions.compose.get()

dependencies {
    compileOnly("org.jetbrains.compose.ui:ui-graphics-desktop:$compose")
    compileOnly(kotlin("stdlib"))
}

val officialUiGraphicsJar = configurations.detachedConfiguration(
    dependencies.create("org.jetbrains.compose.ui:ui-graphics-desktop:$compose")
)
officialUiGraphicsJar.isTransitive = false

val officialUiDesktopJar = configurations.detachedConfiguration(
    dependencies.create("org.jetbrains.compose.ui:ui-desktop:$compose")
)
officialUiDesktopJar.isTransitive = false

val patchedJar = tasks.register<Jar>("patchUiGraphics") {
    description = "Produces a ui-graphics-desktop jar with the skiko-dependent classes replaced by the local implementations."
    // The complete androidx.compose.ui.graphics set is split across two jars: the standalone
    // ui-graphics-desktop (generic classes) and the ui-desktop uber jar (desktop-specific classes
    // like ReusableGraphicsLayerScope). Merge both.
    val src = officialUiDesktopJar.singleFile
    inputs.file(src)
    outputs.file(layout.buildDirectory.file("libs/ui-graphics-desktop-noskiko.jar"))
    from(sourceSets.main.get().output.classesDirs)
    from(zipTree(officialUiGraphicsJar.singleFile)) { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    from(zipTree(src)) {
        include("androidx/compose/ui/graphics/**")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    archiveFileName.set("ui-graphics-desktop-noskiko.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    // jarJar refuses to embed local file dependencies without an explicit Java module name.
    manifest {
        attributes(mapOf("Automatic-Module-Name" to "org.jetbrains.compose.ui.graphics.desktop.noskiko"))
    }
}

val patchedUiDesktop = tasks.register<Jar>("patchUiDesktop") {
    description = "Produces a ui-desktop jar without androidx.compose.ui.graphics so the noskiko " +
        "replacement jar is the sole provider of that package (avoids a JPMS split-package under ModLauncher)."
    val src = officialUiDesktopJar.singleFile
    inputs.file(src)
    outputs.file(layout.buildDirectory.file("libs/ui-desktop-nographics.jar"))
    from(zipTree(src)) { exclude("androidx/compose/ui/graphics/**") }
    archiveFileName.set("ui-desktop-nographics.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    manifest {
        attributes(mapOf("Automatic-Module-Name" to "org.jetbrains.compose.ui.desktop.nographics"))
    }
}

tasks.named("build") { dependsOn(patchedJar, patchedUiDesktop) }
