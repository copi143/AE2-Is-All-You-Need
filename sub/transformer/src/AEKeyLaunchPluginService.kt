package allyouneed.transformer

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap

/**
 * Forge 侧的 AEKey intern 入口：以 [ILaunchPluginService] 的形式拦截每一个被加载的类，
 * 不需要任何启动期扫描去枚举 target，也不需要通过 `ITransformationService`/`ITransformer`
 * 提前拿到 `NEW <keyClass>` 的调用点。
 *
 * [handlesClass] 对所有非空类返回 [ILaunchPluginService.Phase.AFTER]（Mixin 之后），
 * `processClass` 里对没有相关字节码的类快速 no-op；真正需要变换的类（AEKey 派生类 +
 * 含 `new <keyClass>` 的类）由 [NewCallTransformer] 处理。
 *
 * 派生类判定也是**完全惰性**的：不扫描整个 mods 目录，而是按需解析单个类的 superName
 * 链（`getResourceAsStream` 读类头），结果用 [keyCache] 缓存。AEKey 的直接/传递子类因
 * JVM 先加载父类，天然按自底向上顺序缓存命中，无需额外扫描。
 */
class AEKeyLaunchPluginService : ILaunchPluginService {
    private val keyCache = ConcurrentHashMap<String, Boolean>()

    @Volatile
    private var runtimeInstalled = false

    @Volatile
    private var runtimeFailed = false

    private val installing = ThreadLocal.withInitial { false }

    override fun name(): String = "ae2isallyouneed_core"

    override fun handlesClass(classType: Type, isEmpty: Boolean): EnumSet<ILaunchPluginService.Phase> =
        if (isEmpty) EnumSet.noneOf(ILaunchPluginService.Phase::class.java)
        else EnumSet.of(ILaunchPluginService.Phase.AFTER)

    override fun processClass(
        phase: ILaunchPluginService.Phase,
        classNode: ClassNode,
        classType: Type,
    ): Boolean {
        if (phase != ILaunchPluginService.Phase.AFTER) return false
        if (isMixin(classNode)) return false
        ensureRuntime()
        if (runtimeFailed) return false
        cacheKeyFromSuper(classNode.name, classNode.superName)
        return NewCallTransformer.apply(classNode) { name -> isKey(name) } > 0
    }

    private fun isMixin(cn: ClassNode): Boolean {
        val desc = "Lorg/spongepowered/asm/mixin/Mixin;"
        return cn.visibleAnnotations?.any { it.desc == desc } == true ||
            cn.invisibleAnnotations?.any { it.desc == desc } == true
    }

    private fun ensureRuntime() {
        if (runtimeInstalled || runtimeFailed || installing.get()) return
        installing.set(true)
        try {
            RuntimeClasses.install()
            runtimeInstalled = true
        } catch (t: Throwable) {
            runtimeFailed = true
            logger.error("AEKey intern runtime install failed; interning disabled", t)
        } finally {
            installing.set(false)
        }
    }

    private fun cacheKeyFromSuper(name: String, superName: String?) {
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

    private fun isKey(name: String): Boolean {
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

    companion object {
        const val INSTALLED_PROP = "allyouneed.core.transformer"
    }
}
