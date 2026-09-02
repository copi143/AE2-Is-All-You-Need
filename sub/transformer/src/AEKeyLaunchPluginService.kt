package allyouneed.transformer

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.util.EnumSet

/**
 * Forge 侧的 AEKey intern 入口：以 [ILaunchPluginService] 的形式拦截每一个被加载的类，
 * 不需要任何启动期扫描去枚举 target，也不需要通过 `ITransformationService`/`ITransformer`
 * 提前拿到 `NEW <keyClass>` 的调用点。
 *
 * [handlesClass] 对所有非空类返回 [ILaunchPluginService.Phase.AFTER]（Mixin 之后），
 * `processClass` 里对没有相关字节码的类快速 no-op；真正需要变换的类（AEKey 派生类 +
 * 含 `new <keyClass>` 的类）由 [NewCallTransformer] 处理。
 *
 * 派生类判定复用 [KeyResolver]（完全惰性，与 Fabric 侧共用）。
 */
class AEKeyLaunchPluginService : ILaunchPluginService {
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
        KeyResolver.cacheKeyFromSuper(classNode.name, classNode.superName)
        val ae = NewCallTransformer.apply(classNode) { name -> KeyResolver.isKey(name) } > 0
        val rl = NewCallTransformer.applyResourceLocation(classNode) > 0
        return ae || rl
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

    companion object {
        const val INSTALLED_PROP = "allyouneed.core.transformer"
    }
}
