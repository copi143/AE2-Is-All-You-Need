plugins {
    id("multiloader-base")
}

version = "0.1.0+compose.${libs.versions.compose.get().replace('-', '.')}"

val composeRuntime = configurations.create("composeRuntime") {
    isCanBeResolved = true
    isCanBeConsumed = false
    exclude(group = "org.jetbrains.skiko")
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
}.also {
    dependencies {
        it(libs.compose.ui)
        it(libs.compose.ui.graphics)
        it(libs.compose.foundation)
        it(libs.compose.foundation.layout)
        it(libs.compose.animation)
        it(libs.compose.material)
    }
}

dependencies {
    compileOnly(libs.compose.ui.graphics)
}

tasks.named<Jar>("jar") {
    description =
        "Produces a Compose desktop runtime jar without skiko, with the skiko-dependent ui-graphics classes replaced by the local implementations."
    from(composeRuntime.map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(mapOf("Automatic-Module-Name" to "org.jetbrains.compose.desktop.runtime"))
    }
}
