package allyouneed.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

object KeyClassScanner {
    const val AE_KEY = "appeng/api/stacks/AEKey"
    const val AE_ITEM_KEY = "appeng/api/stacks/AEItemKey"
    const val AE_FLUID_KEY = "appeng/api/stacks/AEFluidKey"

    val SEED_KEYS = arrayOf(
        AE_ITEM_KEY,
        AE_FLUID_KEY,
        "allyouneed/logic/aekey/EnergyKey",
        "allyouneed/logic/aekey/ManaKey",
        "allyouneed/logic/aekey/VirtualKey",
    )

    val SEED_SITES = arrayOf(
        "allyouneed/logic/aekey/EnergyKey\$Type",
        "allyouneed/logic/aekey/ManaKey\$Type",
        "allyouneed/logic/aekey/VirtualKey\$Type",
    )

    fun scanKeyClasses(paths: Iterable<Path>): Set<String> {
        val supers = LinkedHashMap<String, String?>()
        for (path in paths) walk(path) { _, bytes ->
            val cr = ClassReader(bytes)
            supers[cr.className] = cr.superName
        }
        val keys = LinkedHashSet<String>()
        for (seed in SEED_KEYS) keys.add(seed)
        for (name in supers.keys) {
            if (inherits(name, AE_KEY, supers)) keys.add(name)
        }
        return keys
    }

    fun findNewCallSites(keyClasses: Set<String>, paths: Iterable<Path>): Set<String> {
        val sites = LinkedHashSet<String>()
        for (seed in SEED_SITES) sites.add(seed)
        for (path in paths) walk(path) { _, bytes ->
            val cr = ClassReader(bytes)
            var found = false
            cr.accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?,
                    ): MethodVisitor? {
                        if (found) return null
                        return object : MethodVisitor(Opcodes.ASM9) {
                            override fun visitTypeInsn(opcode: Int, type: String) {
                                if (opcode == Opcodes.NEW && type in keyClasses) found = true
                            }
                        }
                    }
                },
                ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
            )
            if (found) sites.add(cr.className)
        }
        return sites
    }

    class Scan(val keys: Set<String>, val sites: Set<String>) {
        val targets: Set<String> = LinkedHashSet<String>().apply {
            addAll(keys)
            addAll(sites)
        }
    }

    fun scan(paths: Iterable<Path>): Scan {
        val keys = scanKeyClasses(paths)
        val sites = findNewCallSites(keys, paths)
        logger.info("scan done: {} key types, {} new-sites, {} transform targets", keys.size, sites.size, keys.size + sites.size)
        return Scan(keys, sites)
    }

    private fun inherits(name: String, target: String, supers: Map<String, String?>): Boolean {
        var cur: String? = name
        val seen = HashSet<String>()
        while (cur != null && seen.add(cur)) {
            if (cur == target) return name != target
            cur = supers[cur]
        }
        return false
    }

    private fun walk(root: Path, consumer: (String, ByteArray) -> Unit) {
        if (!Files.exists(root)) return
        if (Files.isDirectory(root)) {
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }.forEach { file ->
                    val rel = root.relativize(file).toString().replace('\\', '/')
                    if (rel != "module-info.class") consumer(rel, Files.readAllBytes(file))
                }
            }
            return
        }
        if (!root.fileName.toString().endsWith(".jar")) return
        ZipInputStream(Files.newInputStream(root)).use { zip -> walkZip(zip, consumer, nested = true) }
    }

    private fun walkZip(zip: ZipInputStream, consumer: (String, ByteArray) -> Unit, nested: Boolean) {
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val name = entry.name
            if (name.endsWith(".class") && name != "module-info.class") {
                consumer(name, zip.readAllBytes())
            } else if (nested && name.endsWith(".jar")) {
                ZipInputStream(ByteArrayInputStream(zip.readAllBytes())).use { inner ->
                    walkZip(inner, consumer, nested = false)
                }
            }
        }
    }
}
