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
}

println("version: $version")
