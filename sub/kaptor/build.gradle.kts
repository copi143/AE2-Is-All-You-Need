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
    implementation(kotlin("stdlib"))
    implementation(libs.antlr.runtime)
    implementation(libs.asm)
    implementation(libs.asm.commons)
    implementation(libs.asm.util)
    implementation("com.google.code.gson:gson:2.10.1")

    compileOnly(libs.slf4j)

    antlr(libs.antlr)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.launcher)
}

val antlrOutDir = layout.buildDirectory.dir("generated-src/antlr/main")

tasks.named("generateGrammarSource") { enabled = false }
val antlrPkgDir = layout.buildDirectory.dir("generated-src/antlr/main/kaptor/parser/antlr")
val a2sPkgDir = layout.buildDirectory.dir("generated-src/antlr/main/kaptor/a2s/parser")

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

val generateA2sLexerGrammarSource = tasks.register<AntlrTask>("generateA2sLexerGrammarSource") {
    description = "Generates A2sLexer from a2s grammar"
    group = "antlr"
    maxHeapSize = "512m"
    source = fileTree("antlr") { include("A2sLexer.g4") }
    outputDirectory = a2sPkgDir.get().asFile
    arguments = listOf("-visitor")
}

val generateA2sParserGrammarSource = tasks.register<AntlrTask>("generateA2sParserGrammarSource") {
    description = "Generates A2sParser from a2s grammar"
    group = "antlr"
    maxHeapSize = "512m"
    source = fileTree("antlr") { include("A2sParser.g4") }
    outputDirectory = a2sPkgDir.get().asFile
    arguments = listOf("-visitor")
    dependsOn(generateA2sLexerGrammarSource)
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
    dependsOn(generateLexerGrammarSource, generateParserGrammarSource, generateA2sLexerGrammarSource, generateA2sParserGrammarSource)
}

tasks.named("compileKotlin") {
    dependsOn(generateLexerGrammarSource, generateParserGrammarSource, generateA2sLexerGrammarSource, generateA2sParserGrammarSource)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(generateLexerGrammarSource, generateParserGrammarSource, generateA2sLexerGrammarSource, generateA2sParserGrammarSource)
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
