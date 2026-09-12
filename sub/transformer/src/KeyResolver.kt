package allyouneed.transformer

import allyouneed.transformer.KeyResolver.keyCache
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

    /**
     * 中间链缓存：类名 -> 它的直接超类名。启动期 3 万+ 类的超链高度重叠
     * （几乎每条链都经过 java/lang/Object 等公共前缀），不缓存中间链等于
     * 每个类都把公共前缀重新 getResourceAsStream + ASM 解析一遍——profile 里
     * ModuleClassLoader.getResource 的热点就是这么来的。
     * ConcurrentHashMap 存不了 null，用 TOP 哨兵表示“到顶（无超类）”。
     */
    private val superCache = ConcurrentHashMap<String, String>()

    /** 不可能是合法类名的哨兵，见 [superCache]。 */
    private const val TOP = "<top>"

    fun cacheKeyFromSuper(name: String, superName: String?) {
        if (keyCache.containsKey(name)) return
        val result = when (superName) {
            Constants.AE_KEY, Constants.AE_KEY_ASM -> {
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
        if (name == Constants.AE_KEY || name == Constants.AE_KEY_ASM) return false
        val seen = HashSet<String>()
        var cur: String? = name
        while (cur != null && seen.add(cur)) {
            if (cur == Constants.AE_KEY || cur == Constants.AE_KEY_ASM) {
                logKey(name, "resolved super chain")
                return true
            }
            // boot 闭包：java.*/jdk.* 类的超类永远还是 boot 层类，而 AEKey 在
            // appeng 包下，不可能出现在它们的上方。直接判否，省掉每条链尾部
            // 1~2 次 getResourceAsStream + 类解析。
            if (cur.startsWith("java/") || cur.startsWith("jdk/")) return false
            cur = resolveSuperName(cur)
        }
        return false
    }

    private fun logKey(name: String, via: String) {
        logger.info("detected AEKey subclass {} ({})", name.replace('/', '.'), via)
    }

    private fun resolveSuperName(name: String): String? {
        superCache[name]?.let { return if (it == TOP) null else it }
        val result = loadSuperName(name)
        superCache[name] = result ?: TOP
        return result
    }

    private fun loadSuperName(name: String): String? {
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
