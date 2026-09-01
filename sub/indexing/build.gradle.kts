plugins {
    id("multiloader-base")
}

group = "allyouneed.indexing"
version = "0.0.0"

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
