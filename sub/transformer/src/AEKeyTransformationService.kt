package allyouneed.transformer

import cpw.mods.modlauncher.LaunchPluginHandler
import cpw.mods.modlauncher.Launcher
import cpw.mods.modlauncher.api.IEnvironment
import cpw.mods.modlauncher.api.ITransformationService
import cpw.mods.modlauncher.api.ITransformer
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService
import java.lang.reflect.Field
import kotlin.reflect.KClass

/**
 * 单 jar `mods/` 部署下，[ILaunchPluginService] 无法被 modlauncher 发现——它只在 BOOT 层
 * 通过 `ServiceLoader.load(ILaunchPluginService, getLayer(BOOT))` 加载，而 mods/ 属于 SERVICE
 * 层。所以用一个 mods/ 可发现的 [ITransformationService] 作壳，在 `initialize` 阶段把真正的
 * [AEKeyLaunchPluginService] 反射注入到 `LaunchPluginHandler.plugins`。
 *
 * 时序是安全的：`initialize` 发生在 `offerScanResultsToPlugins` / `announceLaunch` / 类变换
 * 之前，注入后能拿到完整的 launch-plugin 生命周期。`Launcher` 与 `LaunchPluginHandler` 都来自
 * classpath（unnamed module），反射访问其私有字段无需 `--add-opens`。
 */
class AEKeyTransformationService : ITransformationService {
    override fun name(): String = "ae2isallyouneed_core"

    override fun onLoad(environment: IEnvironment, otherServices: Set<String>) {
        System.setProperty(AEKeyLaunchPluginService.INSTALLED_PROP, "true")
        logger.info("ITransformationService onLoad (launch-plugin injector shell)")
    }

    override fun initialize(environment: IEnvironment) {
        injectLaunchPlugin()
    }

    override fun transformers(): List<ITransformer<*>> = emptyList()

    private fun injectLaunchPlugin() {
        try {
            val launcher = Launcher.INSTANCE
            val launchPlugins = Launcher::class.field("launchPlugins").get(launcher)
            val pluginsField = LaunchPluginHandler::class.field("plugins")

            val plugins = pluginsField.get(launchPlugins) as Map<String, ILaunchPluginService>
            val plugin = AEKeyLaunchPluginService()
            val reordered = LinkedHashMap<String, ILaunchPluginService>().apply {
                putAll(plugins)
                set(plugin.name(), plugin)
            }
            putObject(launchPlugins, pluginsField, reordered)
            logger.info(
                "injected ILaunchPluginService '{}' as last of {} launch plugins", plugin.name(), reordered.size
            )
        } catch (t: Throwable) {
            logger.error("failed to inject ILaunchPluginService '{}'", AEKeyLaunchPluginService.NAME, t)
        }
    }

    private fun putObject(owner: Any, f: Field, value: Any) {
        try {
            f.set(owner, value)
        } catch (_: Throwable) {
            val unsafe = unsafe()
            unsafe.putObject(owner, unsafe.objectFieldOffset(f), value)
        }
    }

    private fun unsafe(): sun.misc.Unsafe {
        val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        f.isAccessible = true
        return f.get(null) as sun.misc.Unsafe
    }

    private fun <T> Class<T>.field(name: String): Field {
        try {
            return getDeclaredField(name).apply {
                isAccessible = true
            }
        } catch (e: NoSuchFieldException) {
            if (superclass != null) {
                return superclass.field(name)
            }
            throw e
        }
    }

    private fun <T : Any> KClass<T>.field(name: String): Field {
        return java.field(name)
    }
}
