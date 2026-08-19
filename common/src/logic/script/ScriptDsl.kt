package allyouneed.logic.script

import kaptor.ScriptEngine
import kaptor.ScriptEngineConfig
import java.nio.file.Path

object ScriptDsl {
    private var engine: ScriptEngine? = null

    fun init(dataDir: Path) {
        if (engine != null) return
        val scriptsDir = dataDir.resolve("scripts")
        val config = ScriptEngineConfig(enableHotReload = true)
        val e = ScriptEngine(config)
        e.init(scriptsDir)
        engine = e
    }

    fun shutdown() {
        engine?.shutdown()
        engine = null
    }

    fun reload() {
        engine?.reloadAll()
    }

    fun dispatchEvent(eventType: String, event: Any?) {
        engine?.dispatchEvent(eventType, event)
    }

    fun getLoadedScripts(): Set<String> = engine?.getLoadedScripts() ?: emptySet()

    fun getStats() = engine?.getStats()
}
