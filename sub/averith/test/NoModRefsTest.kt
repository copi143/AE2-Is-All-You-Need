package averith

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * averith 是通用工具/规划模块，字节码中不得引用任何 AE2 / Minecraft 类型，
 * 防止 mod 依赖被意外引入（本测试不加载类，直接扫描 class 文件内容）。
 */
class NoModRefsTest {
    /** 禁止出现的字节码内部名前缀。 */
    private val forbiddenPrefixes = listOf("appeng/", "net/minecraft/", "net/minecraftforge/")

    @Test
    fun `averith classes have no AE2 or Minecraft refs`() {
        var scanned = 0
        for (root in classRoots()) {
            val classes = root.walkTopDown().filter { it.isFile && it.extension == "class" }
            for (file in classes) {
                scanned++
                val bytes = file.readBytes()
                for (prefix in forbiddenPrefixes) {
                    assertFalse(
                        bytes.containsPattern(prefix),
                        "${file.relativeTo(root)} references $prefix",
                    )
                }
            }
        }
        assertTrue(scanned > 0, "no classes found")
    }

    /**
     * 定位 main 编译输出根目录（.../averith/ 包目录的上级）。
     *
     * 只扫描 main 类：测试类本身含有 [forbiddenPrefixes] 字面量，
     * 扫描 test 输出会造成自我命中。
     */
    private fun classRoots(): List<File> {
        val cl = NoModRefsTest::class.java.classLoader
        val roots = cl.getResources("averith").toList().map { File(it.toURI()) }
        return roots.filter { "/main/" in it.path }.ifEmpty { roots }
    }

    /** 朴素字节串匹配（class 文件中的常量池 UTF8 为裸字节，足以检出内部名引用）。 */
    private fun ByteArray.containsPattern(pattern: String): Boolean {
        val target = pattern.toByteArray()
        outer@ for (i in 0..size - target.size) {
            for (j in target.indices) {
                if (this[i + j] != target[j]) continue@outer
            }
            return true
        }
        return false
    }
}
