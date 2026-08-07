plugins {
    // see https://fabricmc.net/develop/ for new versions
    alias(libs.plugins.loom) apply false
    // see https://projects.neoforged.net/neoforged/moddevgradle for new versions
    alias(libs.plugins.moddev) apply false
}

allprojects {
    version = run {
        fun execGit(vararg args: String): String? = runCatching {
            val proc = ProcessBuilder("git", *args).directory(rootDir).redirectErrorStream(true).start()
            val output = java.io.ByteArrayOutputStream()
            proc.inputStream.use { it.copyTo(output) }
            if (proc.waitFor() != 0) null else output.toString().trim()
        }.getOrNull()

        val time = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss"))

        // Use git describe to find the nearest tag. format: MC_VERSION/MOD_VERSION (e.g. 1.20.1/1.0.0)
        val match = execGit("describe", "--tags", "--long", "--dirty", "--match=*/*")?.let {
            """^(.+?)-(\d+)-g[0-9a-f]+((-dirty)?)$""".toRegex().find(it)
        } ?: return@run "0.0.0+${
            execGit("rev-list", "--count", "HEAD") ?: "unknown"
        }${
            if (execGit("status", "--porcelain")?.isEmpty() ?: true) "" else ".dirty.$time"
        }"

        val version = match.groupValues[1].substringAfter("/")
        val commits = match.groupValues[2].toInt()
        val dirty = match.groupValues[3].isNotEmpty()

        when {
            commits == 0 && !dirty -> version
            commits == 0 && dirty -> "$version+dirty.$time"
            commits != 0 && !dirty -> "$version+$commits"
            else -> "$version+$commits.dirty.$time"
        }
    }

    // Shared repositories for every subproject (declared once here instead of per-module).
    repositories {
        mavenCentral()
        exclusiveContent {
            forRepositories(
                maven {
                    name = "ParchmentMC"
                    url = uri("https://maven.parchmentmc.org/")
                }
            )
            filter { includeGroup("org.parchmentmc.data") }
        }
        // Forge universal jar (net.minecraftforge:forge) is needed compile-only by common/fabric for
        // GTCEu's IForgeBlockEntity hierarchy; keep this repo unfiltered so any group can resolve.
        maven {
            name = "Forge Releases"
            url = uri("https://maven.minecraftforge.net")
        }
        maven {
            name = "BlameJared"
            url = uri("https://maven.blamejared.com")
        }
        maven {
            name = "kotlinforforge"
            url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        }
        maven {
            name = "GTCEu Maven"
            url = uri("https://maven.gtceu.com")
        }
        maven {
            name = "Modrinth"
            url = uri("https://api.modrinth.com/maven")
        }
        maven {
            name = "TerraformersMC"
            url = uri("https://maven.terraformersmc.com/")
        }
        maven {
            name = "ModMaven"
            url = uri("https://modmaven.dev/")
        }
        maven {
            name = "JetBrains Compose"
            url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        }
        maven {
            name = "Google Android"
            url = uri("https://dl.google.com/dl/android/maven2/")
        }
        maven {
            name = "IzzelAliz Maven"
            url = uri("https://maven.izzel.io/releases/")
        }
        maven {
            name = "Architectury Maven"
            url = uri("https://maven.architectury.dev/")
        }
        maven {
            name = "FTB Maven"
            url = uri("https://maven.ftb.dev/releases/")
        }
        maven {
            name = "FirstDarkDev Maven"
            url = uri("https://maven.firstdark.dev/snapshots")
        }
    }
}

println("version: $version")
