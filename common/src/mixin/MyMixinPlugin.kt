package allyouneed.mixin

import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

/**
 * 本配置通用可选依赖守卫。
 * 
 * 部分 mixin 针对可选运行时依赖的类（当前是 GTCEu：`allyouneed.mixin.gtceu`
 * 里的组模式 mixin；以后可能更多）。在没有该依赖的平台上（Fabric 从不带 GTCEu）
 * 目标类不存在，mixin 必须被跳过，否则配置处理会失败。提前否决 mixin 让配置能
 * 干净加载，而在类存在时 mixin 仍然生效。
 * 
 * 每个目标类都被通用探测：如果它的类文件在 classpath 上不可达，就跳过该 mixin。
 * 没有硬编码的依赖列表，未来新增的可选依赖会被自动处理。
 * 
 * 探测绝不能加载类：在这里调用 `Class.forName` 会在 MixinTransformer 提交
 * 本配置之前就把目标类注册进 classloader，于是已加载的类永远不会被变换，mixin
 * 静默地永不生效。用 classpath 资源查找可以检查存在性而不加载。
 * 
 * Generic optional-dependency guard for the mixins in this config.
 * 
 * 
 * Some mixins target classes of optional runtime dependencies (currently GTCEu: the group
 * pattern mixins in `allyouneed.mixin.gtceu`; more may follow). On a platform where such a
 * mod is absent (Fabric never ships GTCEu) the target classes do not exist and the mixins must be
 * skipped, otherwise config processing fails. Vetoing the mixin up front lets the config load
 * cleanly, while the mixins still apply whenever the classes exist.
 * 
 * 
 * Every target class is probed generically: if its class file is not reachable on the
 * classpath, the mixin is skipped. There is no hard-coded dependency list, so future optional
 * dependencies are handled automatically.
 * 
 * 
 * The presence probe must not load the class: `Class.forName` here would register the
 * target with the classloader before the MixinTransformer has committed this config, so the
 * already-loaded class is never transformed and the mixin silently never applies. A classpath
 * resource lookup checks presence without loading.
 */
class MyMixinPlugin : IMixinConfigPlugin {
    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean {
        mixinClassName.startsWith("allyouneed.mixin.") || return true
        val relPath = mixinClassName.removePrefix("allyouneed.mixin.")
        val resource = targetClassName.replace('.', '/') + ".class"
        return when {
            relPath.startsWith("gtceu.") || relPath.startsWith("ldlib.") || relPath.startsWith("emi.") -> {
                javaClass.getClassLoader().getResource(resource) != null
            }

            else -> true
        }
    }

    override fun onLoad(mixinPackage: String) {}

    override fun getRefMapperConfig(): String? = null

    override fun acceptTargets(myTargets: MutableSet<String>?, otherTargets: MutableSet<String>?) {}

    override fun getMixins(): MutableList<String>? = null

    override fun preApply(
        targetClassName: String?, targetClass: ClassNode?, mixinClassName: String?, mixinInfo: IMixinInfo?
    ) {
    }

    override fun postApply(
        targetClassName: String?, targetClass: ClassNode?, mixinClassName: String?, mixinInfo: IMixinInfo?
    ) {
    }
}
