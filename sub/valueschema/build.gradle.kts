plugins {
    id("multiloader-base")
    alias(libs.plugins.ksp)
}

group = "io.github.copi143.valueschema"
version = "0.1.0"

dependencies {
    compileOnly(libs.ksp.api)
    implementation(libs.kotlinpoet)
    kspTest(project(":valueschema"))
}
