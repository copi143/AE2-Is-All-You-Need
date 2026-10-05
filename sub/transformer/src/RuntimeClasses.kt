package allyouneed.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.lang.invoke.MethodHandles

object RuntimeClasses {
    const val PREFIX = "META-INF/inject/"
    const val INDEX = PREFIX + "classes.txt"

    @Volatile
    private var installed = false

    @Volatile
    var gsonInstalled = false
        private set

    @Volatile
    var gsonFactoryHooked = false
        private set

    val gsonCallSitesNeeded: Boolean
        get() = gsonInstalled && !gsonFactoryHooked

    @Volatile
    var mapAccumulationInstalled = false
        private set

    @Synchronized
    fun install() {
        if (installed) return
        val loader = findLoader()
        val names = classNames()
        if (!gsonInstalled) {
            installGson(loader, loader.javaClass.name.contains("TransformingClassLoader"))
        }
        if (MapAccumulationTransformer.enabled && !mapAccumulationInstalled) {
            try {
                val mapClass = Class.forName("it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap", false, loader)
                val merge = mapClass.getMethod(
                    "merge", Any::class.java, Int::class.javaPrimitiveType,
                    java.util.function.BiFunction::class.java,
                )
                check(merge.returnType == Int::class.javaPrimitiveType && merge.declaringClass == mapClass)
                mapClass.install(
                    names.filter { it.startsWith("it.unimi.dsi.fastutil.objects.") }
                        .sortedBy { if (it.endsWith(".IdentityKeyClasses")) 0 else 1 },
                )
                mapAccumulationInstalled = true
                logger.info("installed experimental map accumulation guards")
            } catch (t: Throwable) {
                logger.warn("map accumulation runtime unavailable; leaving original calls intact", t)
            }
        }
        Class.forName("appeng.api.stacks.AEKey", false, loader)
            .install(names.filter { it.startsWith("appeng.api.stacks.") })
        Class.forName("net.minecraft.resources.ResourceLocation", false, loader)
            .install(names.filter { it.startsWith("net.minecraft.resources.") })
        installed = true
    }

    /**
     * 仅安装 gson 快速路径（运行时类 + Forge 下的 RTAF 先发替换）。
     * Forge 的 JarJar 在 mod discovery 早期就会加载 RTAF（Gson POJO 绑定 metadata），
     * 所以 AEKeyTransformationService.onLoad 会以 preempt=true 提前调用本方法抢占时序窗口；
     * 之后的 install() 因 gsonInstalled 已置位而跳过。
     */
    @Synchronized
    fun installGson(loader: ClassLoader, preempt: Boolean) {
        if (gsonInstalled) return
        val names = classNames()
        try {
            // 注意：锚点不能是 ReflectiveTypeAdapterFactory 本身——Class.forName 会触发其加载，
            // 而加载完成前 gsonInstalled 尚未置位，钩子变换会被跳过。TypeAdapters 同包且无此问题。
            val anchor = Class.forName(Constants.GSON_PACKAGE_ANCHOR, false, loader)
            val gsonClasses = names.filter { it.startsWith(Constants.GSON_PACKAGE) }
            val lookup = MethodHandles.privateLookupIn(anchor, MethodHandles.lookup())
            // 先定义入口类并让其自行补 ASM 模块读边（Module.addReads 有 caller 检查，
            // 必须由 gson 模块内的代码发起）。其余类按依赖顺序定义：define 即校验，
            // 校验帧中出现尚未定义的类会 NCDFE，所以被引用的类必须先行定义。
            // 注意：FastFactory 被 GsonFastPath.wrapGson 的方法帧引用，必须在入口类之前定义；
            // 它只通过 invokestatic 引用 GsonFastPath（惰性解析），不构成循环。
            val entryPoint = Constants.GSON_PACKAGE + "GsonFastPath"
            val ordered = gsonClasses.sortedBy {
                when {
                    it.endsWith(".FastFactory") || it.endsWith(".FastAdapterCache") -> 0
                    it == entryPoint -> 1
                    it.endsWith(".FrameSafeClassWriter") -> 2
                    it.endsWith(".FastAdapterEntry") -> 3
                    // StreamJsonObject 的异常表引用 FallbackSignal，校验时须已定义
                    it.endsWith(".FallbackSignal") -> 4
                    else -> 5
                }
            }
            var found = false
            for (name in ordered) {
                // This superclass links Gson's Adapter nest; define it only after pre-emption.
                if (name.endsWith(".FastReflectiveAdapter")) continue
                define(lookup, loader, name)
                if (name.endsWith(".FastFactory")) {
                    // FastFactory 的 clinit 为空，初始化安全；由其补 ASM 模块读边
                    // （后续 FrameSafeClassWriter 的 superclass 检查需要）
                    Class.forName(name, true, loader).getMethod("prepareModules").invoke(null)
                }
                if (name == entryPoint) found = true
            }
            check(found) { "$entryPoint missing from inject index" }
            // 必须先替换 RTAF 再初始化 GsonFastPath：其 clinit 的元数据反射会加载
            // RTAF$1/RTAF$BoundField，nest host 校验随之加载 RTAF 本体，抢占就失败了。
            if (preempt) preemptRtaf(lookup, loader)
            // 置位必须在初始化之前：初始化连带加载 RTAF 本体，Fabric 下该类经 Knot
            // 变换管线时要求 gsonInstalled 已置位，否则钩子变换被跳过。
            gsonInstalled = true
            ordered.filter { it.endsWith(".FastReflectiveAdapter") }.forEach { define(lookup, loader, it) }
            val entryClass = Class.forName(entryPoint, true, loader)
            gsonFactoryHooked = entryClass.getMethod("factoryHooked").invoke(null) as Boolean
            if (gsonFactoryHooked) logger.info("gson factory hook confirmed; construction-site fallback disabled")
            logger.info("installed gson fast path runtime classes")
        } catch (t: Throwable) {
            logger.error("gson fast path runtime install failed; gson fast path disabled", t)
        }
    }

    private fun Class<*>.install(inject: List<String>) {
        val self = RuntimeClasses::class.java.module
        val other = this.module
        if (!self.canRead(other)) self.addReads(other)
        val lookup = MethodHandles.privateLookupIn(this, MethodHandles.lookup())
        for (name in inject) {
            define(lookup, this.classLoader, name)
            logger.info(
                "defined intern runtime classes: {} into module {} (loader {})",
                name,
                other.name,
                other.classLoader.javaClass.name,
            )
        }
    }

    private fun classNames(): List<String> {
        val text =
            RuntimeClasses::class.java.classLoader.getResourceAsStream(INDEX)?.use { it.readBytes().decodeToString() }
                ?: throw IllegalStateException("missing $INDEX")
        return text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }

    internal fun findLoader(): ClassLoader {
        var cl: ClassLoader? = Thread.currentThread().contextClassLoader
        while (cl != null) {
            if (cl.javaClass.name.contains("TransformingClassLoader")) return cl
            cl = cl.parent
        }
        return Thread.currentThread().contextClassLoader ?: RuntimeClasses::class.java.classLoader
    }

    /**
     * Forge 专属：BOOT 层无变换管线，而在 JarJar 读取 metadata 之前这些 gson 内部类尚未
     * 加载——直接读取 gson.jar 中的原始类字节，用与 Fabric 相同的变换（RTAF 钩子 +
     * Streams.write 流式钩子）后抢先 define，原版类便永远不会加载（加载器字典先到先得）。
     *
     * Fabric 不使用本路径：这些类经 Knot 变换管线（保持 mixin 兼容），先发定义会绕过 mixin。
     * 单个类的任何失败（资源缺失、特征不匹配、已被加载）只跳过该类，互不影响。
     */
    private fun preemptRtaf(lookup: MethodHandles.Lookup, loader: ClassLoader) {
        // Lookup.defineClass 要求与目标类同包：Streams 需以 com.google.gson.internal
        // 包内的类为锚点另建 lookup
        val targets = buildList<Pair<String, (ClassNode) -> Boolean>> {
            if (JsonStreamTransformer.needsStreamsHook) {
                add(Constants.GSON_STREAMS to { cn -> JsonStreamTransformer.applyStreamsHook(cn) })
            }
            add(Constants.GSON_RTAF to { cn -> GsonFastPathTransformer.apply(cn) })
        }
        for ((name, transform) in targets) {
            try {
                val anchorName = if (name.startsWith("com/google/gson/internal/bind/")) {
                    null
                } else {
                    "com.google.gson.internal.ConstructorConstructor"
                }
                val targetLookup = if (anchorName == null) {
                    lookup
                } else {
                    MethodHandles.privateLookupIn(Class.forName(anchorName, false, loader), MethodHandles.lookup())
                }
                val bytes = loader.getResourceAsStream("$name.class")?.use { it.readBytes() }
                if (bytes == null) {
                    logger.warn("gson fast path: {} class bytes not found; leaving it stock", name)
                    continue
                }
                val cr = ClassReader(bytes)
                val cn = ClassNode()
                cr.accept(cn, ClassReader.SKIP_FRAMES)
                if (!transform(cn)) continue
                val cw = ClassWriter(cr, ClassWriter.COMPUTE_FRAMES)
                cn.accept(cw)
                targetLookup.defineClass(cw.toByteArray())
                logger.info("pre-emptively installed hooked {} into gson module", name.replace('/', '.'))
            } catch (e: LinkageError) {
                logger.warn("gson fast path: {} already loaded ({}); leaving it stock", name, e.message)
            } catch (t: Throwable) {
                logger.warn("gson fast path: {} pre-emption failed; leaving it stock", name, t)
            }
        }
    }

    private fun define(lookup: MethodHandles.Lookup, loader: ClassLoader, name: String) {
        try {
            Class.forName(name, false, loader)
            return
        } catch (_: ClassNotFoundException) {
        }
        val path = PREFIX + name.replace('.', '/') + ".class"
        val bytes = RuntimeClasses::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
            ?: throw IllegalStateException("missing $path")
        lookup.defineClass(bytes)
    }
}
