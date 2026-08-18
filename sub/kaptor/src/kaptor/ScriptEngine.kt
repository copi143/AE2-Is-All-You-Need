package kaptor

import kaptor.compiler.SchemaBuilder
import kaptor.lsp.ScriptLanguageService
import kaptor.runtime.EventAccessor
import kaptor.runtime.ScriptEventBus
import kaptor.runtime.ScriptManager
import kaptor.runtime.ScriptStats
import java.nio.file.Path

class ScriptEngine(private val config: ScriptEngineConfig = ScriptEngineConfig()) {
    private var initialized = false

    fun declareEvent(eventType: String, builder: SchemaBuilder.() -> Unit) {
        val schema = SchemaBuilder().apply(builder).build()
        EventAccessor.registerEvent(eventType, schema)
        ScriptEventBus.storeEventSchema(eventType, schema)
    }

    fun init(scriptsDir: Path) {
        if (initialized) return
        initialized = true

        if (!scriptsDir.toFile().exists()) {
            scriptsDir.toFile().mkdirs()
        }
        ScriptManager.init(scriptsDir, config.logger)
        ScriptManager.loadAllScripts()
        if (config.enableHotReload) {
            ScriptManager.startHotReload()
        }

        config.logger.info("ScriptEngine initialized with ${ScriptManager.getStats().loadedScripts} scripts")
    }

    fun shutdown() {
        ScriptManager.stopHotReload()
        ScriptEventBus.clearAll()
        EventAccessor.clearAll()
        initialized = false
    }

    fun reloadAll() {
        ScriptManager.stopHotReload()
        ScriptEventBus.clearAll()
        EventAccessor.clearAll()
        ScriptManager.loadAllScripts()
        if (config.enableHotReload) {
            ScriptManager.startHotReload()
        }
    }

    fun dispatchEvent(eventType: String, event: Any?) {
        ScriptEventBus.dispatchEvent(eventType, event)
    }

    fun getLoadedScripts(): Set<String> = ScriptManager.getLoadedScripts()

    fun getStats(): ScriptStats = ScriptManager.getStats()

    fun getLanguageService(): ScriptLanguageService = ScriptManager.getLanguageService()
}

data class ScriptEngineConfig(
    val logger: ScriptLogger = createLogger(),
    val enableHotReload: Boolean = true
)
