plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle)
    implementation(libs.dokka.gradle)
    implementation(files((libs as Any).javaClass.superclass.protectionDomain.codeSource.location))
}

repositories {
    mavenCentral()
}
