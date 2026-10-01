package gsonfast

import java.io.File

fun gsonJar(): File {
    val loc = com.google.gson.Gson::class.java.protectionDomain.codeSource.location
    return File(loc.toURI())
}

fun resourceBytes(path: String): ByteArray =
    Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.use { it.readBytes() }
        ?: error("missing test resource $path")
