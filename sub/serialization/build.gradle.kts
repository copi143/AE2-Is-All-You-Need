plugins {
    id("multiloader-base")
    alias(libs.plugins.ksp)
    `maven-publish`
    id("signing")
}

group = "io.github.copi143.serialization"
version = "1.0.0"

dependencies {
    compileOnly(libs.ksp.api)
    implementation(libs.kotlinpoet)
    // KSP for test fixtures only — main stays minecraft-free
    kspTest(project(":serialization"))
    // Minecraft only for test — main remains pure Kotlin
    // Provided via test source stubs (net/minecraft/**) + netty/fastutil
    testImplementation("it.unimi.dsi:fastutil:8.5.9")
    testImplementation(libs.netty.codec.http)
    testImplementation(libs.asm.tree)
    testImplementation("io.netty:netty-buffer:4.1.82.Final")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.copi143.serialization"
            artifactId = "serialization"
            version = project.version.toString()
            from(components["java"])
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))
            pom {
                name.set("Serialization - Compile-time NBT/Packet Serialization for Kotlin")
                description.set("Annotation-driven compile-time serialization for Minecraft NBT and FriendlyByteBuf")
                url.set("https://github.com/copi143/AE2-Is-All-You-Need")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("copi143")
                        name.set("copi143")
                    }
                }
                scm {
                    url.set("https://github.com/copi143/AE2-Is-All-You-Need.git")
                    connection.set("scm:git:https://github.com/copi143/AE2-Is-All-You-Need.git")
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}

publishing {
    repositories {
        mavenCentral()
    }
}
