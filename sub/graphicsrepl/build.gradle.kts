plugins {
    id("multiloader-base")
}

version = "0.1.0+compose.${libs.versions.compose.get().replace('-', '.')}"

dependencies {
    compileOnly(libs.compose.ui.graphics)
    compileOnly(kotlin("stdlib"))
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
        "Produces a ui-graphics-desktop jar with the skiko-dependent classes replaced by the local implementations."
//    val src = officialUiDesktopJar.singleFile
//    inputs.file(src)
    from(sourceSets.main.get().output.classesDirs)
    from(zipTree(officialUiGraphicsJar.singleFile)) { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    from(zipTree(officialUiDesktopJar.singleFile)) {
        include("androidx/compose/ui/graphics/**")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    manifest {
        attributes(mapOf("Automatic-Module-Name" to "org.jetbrains.compose.ui.graphics.desktop.noskiko"))
    }
}
