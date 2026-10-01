plugins {
    id("multiloader-base")
    alias(libs.plugins.ksp)
    alias(libs.plugins.jmh)
}

group = "io.github.copi143.valueschema"
version = "0.1.0"

sourceSets.named("jmh") {
    kotlin.setSrcDirs(listOf("jmh"))
    resources.setSrcDirs(listOf("jmh/resources"))
}

dependencies {
    compileOnly(libs.ksp.api)
    implementation(libs.kotlinpoet)
    kspTest(project(":valueschema"))
    jmhImplementation(sourceSets.test.get().output)
}

jmh {
    fork = 1
    warmupIterations = 1
    iterations = 2
    profilers = listOf("gc")
}
