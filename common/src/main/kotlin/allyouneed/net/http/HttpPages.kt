package allyouneed.net.http

import java.util.concurrent.CopyOnWriteArrayList

fun interface ClasspathOpen {
    fun open(path: String): ByteArray?
}

class StaticMatch(
    val bytes: ByteArray,
    val contentType: String,
    val resource: String,
)

class PageEntry(
    val kind: String,
    val url: String,
    val source: String,
)

object HttpPages {
    private class Mount(val prefix: String, val classpathRoot: String)
    private class File(val path: String, val classpathFile: String)
    private class Memory(val path: String, val contentType: String, val bytes: ByteArray)

    private val mounts = CopyOnWriteArrayList<Mount>()
    private val files = CopyOnWriteArrayList<File>()
    private val memories = CopyOnWriteArrayList<Memory>()

    @Volatile
    var opener: ClasspathOpen = ClasspathOpen(::openClasspath)

    fun mount(urlPrefix: String, classpathRoot: String) {
        val prefix = HttpCall.normalizePath(urlPrefix)
        mounts.removeIf { it.prefix == prefix }
        mounts += Mount(prefix, classpathRoot.trim('/'))
    }

    fun page(path: String, classpathFile: String) {
        val normalized = HttpCall.normalizePath(path)
        files.removeIf { it.path == normalized }
        memories.removeIf { it.path == normalized }
        files += File(normalized, classpathFile.trim('/'))
    }

    fun bytes(path: String, contentType: String, data: ByteArray) {
        val normalized = HttpCall.normalizePath(path)
        files.removeIf { it.path == normalized }
        memories.removeIf { it.path == normalized }
        memories += Memory(normalized, contentType, data)
    }

    fun match(path: String): StaticMatch? {
        val normalized = HttpCall.normalizePath(path)
        memories.firstOrNull { it.path == normalized }?.let {
            return StaticMatch(it.bytes, it.contentType, it.path)
        }
        files.firstOrNull { it.path == normalized }?.let { file ->
            val data = opener.open(file.classpathFile) ?: return@let
            return StaticMatch(data, mime(file.classpathFile), file.classpathFile)
        }
        val mount = mounts
            .filter { covers(it.prefix, normalized) }
            .maxByOrNull { it.prefix.length }
            ?: return null
        val relative = relativeTo(mount.prefix, normalized) ?: return null
        val joined = safeJoin(mount.classpathRoot, indexIfEmpty(relative)) ?: return null
        val data = opener.open(joined) ?: run {
            val asIndex = safeJoin(mount.classpathRoot, indexIfEmpty("$relative/index.html")) ?: return null
            opener.open(asIndex)?.let { return StaticMatch(it, mime(asIndex), asIndex) }
            return null
        }
        return StaticMatch(data, mime(joined), joined)
    }

    fun list(): List<PageEntry> {
        val out = ArrayList<PageEntry>(memories.size + files.size + mounts.size)
        memories.forEach { out += PageEntry("bytes", it.path, it.contentType) }
        files.forEach { out += PageEntry("page", it.path, it.classpathFile) }
        mounts.forEach { out += PageEntry("mount", it.prefix, it.classpathRoot) }
        return out
    }

    internal fun clear() {
        mounts.clear()
        files.clear()
        memories.clear()
        opener = ClasspathOpen(::openClasspath)
    }

    internal fun safeJoin(root: String, relative: String): String? {
        val parts = relative.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == "." || it == ".." || it.contains('\\') || it.contains('\u0000') }) return null
        val base = root.trim('/')
        return if (parts.isEmpty()) base else "$base/${parts.joinToString("/")}"
    }

    private fun covers(prefix: String, path: String): Boolean =
        prefix == "/" || path == prefix || path.startsWith("$prefix/")

    private fun relativeTo(prefix: String, path: String): String? {
        if (prefix == "/") return if (path == "/") "" else path.trimStart('/')
        if (path == prefix) return ""
        if (path.startsWith("$prefix/")) return path.substring(prefix.length + 1)
        return null
    }

    private fun indexIfEmpty(relative: String): String =
        if (relative.isEmpty() || relative.endsWith('/')) {
            val trimmed = relative.trimEnd('/')
            if (trimmed.isEmpty()) "index.html" else "$trimmed/index.html"
        } else {
            relative
        }

    internal fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html; charset=UTF-8"
        "js", "mjs" -> "text/javascript; charset=UTF-8"
        "css" -> "text/css; charset=UTF-8"
        "json", "map" -> "application/json; charset=UTF-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "ico" -> "image/x-icon"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "txt" -> "text/plain; charset=UTF-8"
        else -> "application/octet-stream"
    }

    private fun openClasspath(path: String): ByteArray? {
        val cl = Thread.currentThread().contextClassLoader ?: HttpPages::class.java.classLoader
        return cl.getResourceAsStream(path)?.use { it.readBytes() }
    }
}
