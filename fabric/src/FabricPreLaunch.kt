package allyouneed

import allyouneed.transformer.ComponentJsonTransformer
import allyouneed.transformer.Constants
import allyouneed.transformer.GsonFastPathTransformer
import allyouneed.transformer.JsonStreamTransformer
import allyouneed.transformer.KeyResolver
import allyouneed.transformer.NewCallTransformer
import allyouneed.transformer.RuntimeClasses
import allyouneed.transformer.logger
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import sun.misc.Unsafe
import java.lang.reflect.Proxy

/**
 * Fabric 侧的 AEKey intern 入口。
 *
 * 与 Forge 侧不同，Fabric 没有 `ILaunchPluginService`。这里直接反射拿到 Knot 的
 * `KnotClassDelegate.mixinTransformer`，用 Proxy 把 Mixin 的 `transformClassBytes` 包一层：
 * 先让 Mixin 完成 mixin 注入，再对我们的目标类（AEKey 派生类 + 含 `new <keyClass>` 的类）
 * 做 intern 重写——即"加载一个类、处理一个类"，且变换发生在 Mixin 之后。
 *
 * 派生类判定复用 [KeyResolver]（完全惰性，与 Forge 侧共用）。
 */
class FabricPreLaunch : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        CommonMain.beforeAllMods()
        try {
            install()
        } catch (t: Throwable) {
            logger.error("AEKey intern transformer not installed; falling back to stock constructors", t)
        }
    }

    private fun install() {
        // 必须先装代理再安装 gson 运行时：GsonFastPath 初始化会做元数据反射，
        // 连带加载 RTAF 内部类与 nest host RTAF 本体；只有代理已就位，RTAF 才能
        // 经变换管线打上钩子（gsonInstalled 已在初始化前置位，见 RuntimeClasses）。
        val knot = Thread.currentThread().contextClassLoader
        val delegate = field(knot, "delegate") ?: throw IllegalStateException("Knot delegate missing")
        val mixinTransformer =
            field(delegate, "mixinTransformer") ?: throw IllegalStateException("mixinTransformer missing")
        val wrapped = wrapMixinTransformer(mixinTransformer)
        putField(delegate, "mixinTransformer", wrapped)
        logger.info("Wrapped Knot mixin transformer for lazy AEKey intern (post-mixin)")
        RuntimeClasses.install()
    }

    private fun wrapMixinTransformer(original: Any): Any {
        val iface = original.javaClass.interfaces.firstOrNull {
            it.name == "org.spongepowered.asm.mixin.transformer.IMixinTransformer"
        } ?: throw IllegalStateException("IMixinTransformer not found on ${original.javaClass.name}")
        return Proxy.newProxyInstance(original.javaClass.classLoader, arrayOf(iface)) { _, method, args ->
            val result = if (args == null) method.invoke(original) else method.invoke(original, *args)
            if (method.name == "transformClassBytes" && result is ByteArray) {
                intern(result)
            } else {
                result
            }
        }
    }

    private fun intern(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        val cn = ClassNode()
        cr.accept(cn, 0)
        if (cn.name.startsWith("allyouneed/transformer/")) return bytes
        if (isMixin(cn)) return bytes
        if (cn.name == Constants.GSON_RTAF) {
            if (!RuntimeClasses.gsonInstalled || !GsonFastPathTransformer.apply(cn)) return bytes
            val cw = ClassWriter(cr, ClassWriter.COMPUTE_FRAMES)
            cn.accept(cw)
            return cw.toByteArray()
        }
        KeyResolver.cacheKeyFromSuper(cn.name, cn.superName)
        val ae = NewCallTransformer.apply(cn) { name -> KeyResolver.isKey(name) }
        val rl = NewCallTransformer.applyResourceLocation(cn)
        // RTAF 直接钩子失效时的冗余保障：双包装是幂等的（FastFactory.wrap 对生成适配器原样返回）
        val gsonSites = if (RuntimeClasses.gsonInstalled) GsonFastPathTransformer.applyCallSites(cn) else 0
        val componentJson = ComponentJsonTransformer.isTarget(cn.name) && ComponentJsonTransformer.apply(cn)
        // 声明式 JSON 序列化流式化 + Streams.write 流式钩子（经 Knot 管线，与 mixin 兼容）
        val jsonStream = RuntimeClasses.gsonInstalled &&
            JsonStreamTransformer.isTarget(cn.name) && JsonStreamTransformer.apply(cn)
        val rewritten = ae + rl + gsonSites
        if (rewritten == 0 && !componentJson && !jsonStream) return bytes
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_FRAMES)
        cn.accept(cw)
        return cw.toByteArray()
    }

    private fun isMixin(cn: ClassNode): Boolean {
        val desc = "Lorg/spongepowered/asm/mixin/Mixin;"
        return cn.visibleAnnotations?.any { it.desc == desc } == true || cn.invisibleAnnotations?.any { it.desc == desc } == true
    }

    private fun field(owner: Any, name: String): Any? {
        var c: Class<*>? = owner.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(owner)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }

    private fun putField(owner: Any, name: String, value: Any) {
        var c: Class<*>? = owner.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                val unsafe = unsafe()
                unsafe.putObject(owner, unsafe.objectFieldOffset(f), value)
                return
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        throw NoSuchFieldException(name)
    }

    private fun unsafe(): Unsafe {
        val f = Unsafe::class.java.getDeclaredField("theUnsafe")
        f.isAccessible = true
        return f.get(null) as Unsafe
    }
}
