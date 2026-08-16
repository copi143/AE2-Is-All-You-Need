package allyouneed.core

import allyouneed.transformer.KeyClassScanner

import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertTrue

class KeyClassScannerTest {
    @Test
    fun `scan finds subclasses and new sites`() {
        val root = tmp()
        writeClass(root, "appeng/api/stacks/AEKey", "java/lang/Object")
        writeClass(root, "appeng/api/stacks/AEItemKey", "appeng/api/stacks/AEKey")
        writeClass(root, KeyClassScanner.AE_KEY_ASM, "appeng/api/stacks/AEKey")
        writeKeyUser(root, "demo/UsesKey", "appeng/api/stacks/AEItemKey")
        writeClass(root, "demo/Unrelated", "java/lang/Object")

        val keys = KeyClassScanner.scanKeyClasses(listOf(root))
        assertTrue("appeng/api/stacks/AEItemKey" in keys)
        assertTrue("appeng/api/stacks/AEFluidKey" in keys)
        assertTrue("appeng/api/stacks/AEKey" !in keys)
        assertTrue(KeyClassScanner.AE_KEY_ASM !in keys)

        val sites = KeyClassScanner.findNewCallSites(keys, listOf(root))
        assertTrue("demo/UsesKey" in sites)
        assertTrue("demo/Unrelated" !in sites)
        assertTrue("allyouneed/logic/aekey/EnergyKey" in keys)
        assertTrue("allyouneed/logic/aekey/EnergyKey\$Type" in sites)
    }

    @Test
    fun `scan reads one level of nested jars`() {
        val root = tmp()
        val nested = root.resolve("nested")
        Files.createDirectories(nested)
        writeClass(nested, "demo/NestedKey", "appeng/api/stacks/AEKey")
        writeClass(nested, "appeng/api/stacks/AEKey", "java/lang/Object")
        val inner = root.resolve("game.jar")
        zipDir(nested, inner)
        val wrap = root.resolve("wrap.jar")
        ZipOutputStream(Files.newOutputStream(wrap)).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/mod/game.jar"))
            zip.write(Files.readAllBytes(inner))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("readme.txt"))
            zip.write("skip".toByteArray())
            zip.closeEntry()
        }
        val keys = KeyClassScanner.scanKeyClasses(listOf(wrap))
        assertTrue("demo/NestedKey" in keys)
    }

    private fun tmp(): Path {
        val cwd = Path.of(System.getProperty("user.dir"))
        val base = if (cwd.fileName.toString() == "common") cwd.parent else cwd
        val dir = base.resolve(".tmp/key-interner-scan")
        if (Files.exists(dir)) dir.toFile().deleteRecursively()
        Files.createDirectories(dir)
        return dir
    }

    private fun writeClass(root: Path, internal: String, superName: String) {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internal, null, superName, null)
        cw.visitEnd()
        val file = root.resolve("$internal.class")
        Files.createDirectories(file.parent)
        Files.write(file, cw.toByteArray())
    }

    private fun writeKeyUser(root: Path, internal: String, key: String) {
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "make", "()L$key;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, key)
        mv.visitInsn(Opcodes.DUP)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, key, "<init>", "()V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(2, 0)
        mv.visitEnd()
        cw.visitEnd()
        val file = root.resolve("$internal.class")
        Files.createDirectories(file.parent)
        Files.write(file, cw.toByteArray())
    }

    private fun zipDir(dir: Path, dest: Path) {
        ZipOutputStream(Files.newOutputStream(dest)).use { zip ->
            Files.walk(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { file ->
                    zip.putNextEntry(ZipEntry(dir.relativize(file).toString().replace('\\', '/')))
                    zip.write(Files.readAllBytes(file))
                    zip.closeEntry()
                }
            }
        }
    }
}
