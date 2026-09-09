plugins {
    id("multiloader-base")
}

group = "allyouneed.client.msdftext"
version = "0.0.0"

dependencies {
    // lwjgl for GlyphAtlas (GL11/GL12/MemoryUtil) — compileOnly because MC runtime provides it
    compileOnly("org.lwjgl:lwjgl:3.3.1")
    compileOnly("org.lwjgl:lwjgl-opengl:3.3.1")

    compileOnly(libs.slf4j)
}

configurations.create("msdftextClasses") {
    isCanBeResolved = false
    isCanBeConsumed = true
}

artifacts {
    add("msdftextClasses", layout.buildDirectory.dir("classes/kotlin/main").map { it.asFile }) {
        builtBy(tasks.named("compileKotlin"))
    }
}
