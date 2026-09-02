package allyouneed.transformer

import org.objectweb.asm.ClassReader
import java.util.concurrent.ConcurrentHashMap

/**
 * 惰性的 AEKey 派生类判定：不扫描整个 classpath，而是按需解析单个类的 superName 链
 * （`getResourceAsStream` 读类头），结果用 [keyCache] 缓存。AEKey 的直接/传递子类因
 * JVM 先加载父类，天然按自底向上顺序缓存命中，无需额外扫描。
 *
 * Forge（`AEKeyLaunchPluginService`）与 Fabric（`FabricPreLaunch`）共用。
 */
object KeyResolver {
    private val keyCache = ConcurrentHashMap<String, Boolean>()

    fun cacheKeyFromSuper(name: String, superName: String?) {
        if (keyCache.containsKey(name)) return
        val result = when (superName) {
            KeyClassScanner.AE_KEY, KeyClassScanner.AE_KEY_ASM -> {
                logKey(name, "direct AEKey subclass")
                true
            }
            null -> false
            else -> if (isKey(superName)) {
                logKey(name, "subclass of ${superName.replace('/', '.')}")
                true
            } else {
                false
            }
        }
        keyCache[name] = result
    }

    fun isKey(name: String): Boolean {
        keyCache[name]?.let { return it }
        val result = computeIsKey(name)
        keyCache[name] = result
        return result
    }

    private fun computeIsKey(name: String): Boolean {
        if (name == KeyClassScanner.AE_KEY || name == KeyClassScanner.AE_KEY_ASM) return false
        if (name in KeyClassScanner.SEED_KEYS) {
            logKey(name, "seed")
            return true
        }
        val seen = HashSet<String>()
        var cur: String? = name
        while (cur != null && seen.add(cur)) {
            if (cur == KeyClassScanner.AE_KEY || cur == KeyClassScanner.AE_KEY_ASM) {
                logKey(name, "resolved super chain")
                return true
            }
            cur = resolveSuperName(cur)
        }
        return false
    }

    private fun logKey(name: String, via: String) {
        logger.info("detected AEKey subclass {} ({})", name.replace('/', '.'), via)
    }

    private fun resolveSuperName(name: String): String? {
        val bytes = try {
            RuntimeClasses.findLoader().getResourceAsStream("$name.class")?.use { it.readBytes() }
        } catch (_: Throwable) {
            null
        } ?: return null
        return try {
            ClassReader(bytes).superName
        } catch (_: Throwable) {
            null
        }
    }
}
