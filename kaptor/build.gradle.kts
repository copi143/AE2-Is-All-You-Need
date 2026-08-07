plugins {
    id("java-library")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm")
    id("antlr")
}

group = "io.github.allyouneed"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.antlr:antlr4-runtime:4.9.1")
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-commons:9.8")
    implementation("org.ow2.asm:asm-util:9.8")
    implementation("com.google.code.gson:gson:2.10.1")

    compileOnly("org.slf4j:slf4j-api:2.0.9")

    antlr("org.antlr:antlr4:4.9.1") // 由于 forge 依赖，从 4.13.2 调整为 4.9.1

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.5")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
}

val antlrOutDir = layout.buildDirectory.dir("generated-src/antlr/main")

tasks.named("generateGrammarSource") { enabled = false }
val antlrPkgDir = layout.buildDirectory.dir("generated-src/antlr/main/kaptor/parser/antlr")

val generateLexerGrammarSource = tasks.register<AntlrTask>("generateLexerGrammarSource") {
    description = "Generates KotlinLexer from official ANTLR grammar"
    group = "antlr"
    maxHeapSize = "512m"
    source = fileTree("antlr") { include("KotlinLexer.g4") }
    outputDirectory = antlrPkgDir.get().asFile
    arguments = listOf("-visitor")
}

val generateParserGrammarSource = tasks.register<AntlrTask>("generateParserGrammarSource") {
    description = "Generates KotlinParser from official ANTLR grammar"
    group = "antlr"
    maxHeapSize = "512m"
    source = fileTree("antlr") { include("KotlinParser.g4") }
    outputDirectory = antlrPkgDir.get().asFile
    arguments = listOf("-visitor")
    dependsOn(generateLexerGrammarSource)
}

sourceSets {
    main {
        kotlin.setSrcDirs(listOf("src"))
        kotlin.srcDir(antlrOutDir)
    }
    test {
        kotlin.setSrcDirs(listOf("test"))
    }
}

tasks.named("compileJava") {
    dependsOn(generateLexerGrammarSource, generateParserGrammarSource)
}

tasks.named("compileKotlin") {
    dependsOn(generateLexerGrammarSource, generateParserGrammarSource)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(generateLexerGrammarSource, generateParserGrammarSource)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "kaptor"
        }
    }
}
