package minecraftx.compose.theme

import allyouneed.client.compose.platform.ComposeFrameDriver
import allyouneed.util.MODID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
import java.util.Properties

enum class McThemeId(val id: String) {
    Dark("dark"),
    Light("light"),
    ;

    val scheme: McColorScheme
        get() = when (this) {
            Dark -> DarkColorScheme
            Light -> LightColorScheme
        }

    companion object {
        fun fromId(raw: String?): McThemeId =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: Dark
    }
}

object McThemeSettings {
    private const val KEY = "theme"
    private const val ENGINE_KEY = "textEngine"
    private const val STYLE_KEY = "style"
    private const val UPDATE_MODE_KEY = "uiUpdateMode"
    private var loaded = false

    private var idState by mutableStateOf(McThemeId.Dark)
    private var engineIdState by mutableStateOf("vanilla")
    private var styleIdState by mutableStateOf(MaterialMcStyle.id)
    private var updateModeState by mutableStateOf(ComposeFrameDriver.UpdateMode.PARALLEL)

    val id: McThemeId
        get() {
            ensureLoaded()
            return idState
        }

    /** Active component style strategy (global default; overridable per subtree via [McTheme]). */
    var style: McStyle
        get() {
            ensureLoaded()
            return McStyles.byId(styleIdState)
        }
        set(value) {
            ensureLoaded()
            if (styleIdState == value.id) return
            styleIdState = value.id
            save()
        }

    /** Raw active text-engine id (resolved leniently by [minecraftx.compose.text.McTextEngines]). */
    var textEngineId: String
        get() {
            ensureLoaded()
            return engineIdState
        }
        set(value) {
            ensureLoaded()
            if (engineIdState == value) return
            engineIdState = value
            save()
        }

    /**
     * UI 更新模式：PARALLEL = 世界渲染阶段在 worker 线程并行更新+录制；GUI_STAGE = GUI 阶段
     * 同步更新+录制。运行时可安全切换（PARALLEL→GUI_STAGE 会先 join 未完成的 worker 任务）。
     */
    var updateMode: ComposeFrameDriver.UpdateMode
        get() {
            ensureLoaded()
            return updateModeState
        }
        set(value) {
            ensureLoaded()
            if (updateModeState == value) return
            updateModeState = value
            ComposeFrameDriver.updateMode = value
            save()
        }

    val colorScheme: McColorScheme
        get() = id.scheme

    fun set(next: McThemeId) {
        ensureLoaded()
        if (idState == next) return
        idState = next
        save()
    }

    fun toggle() {
        set(if (id == McThemeId.Dark) McThemeId.Light else McThemeId.Dark)
    }

    private fun ensureLoaded() {
        if (loaded) return
        val file = configFile() ?: return
        if (!file.isFile) {
            loaded = true
            return
        }
        val ok = runCatching {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            idState = McThemeId.fromId(props.getProperty(KEY))
            engineIdState = props.getProperty(ENGINE_KEY) ?: engineIdState
            styleIdState = props.getProperty(STYLE_KEY) ?: styleIdState
            updateModeState = runCatching {
                ComposeFrameDriver.UpdateMode.valueOf(props.getProperty(UPDATE_MODE_KEY) ?: "")
            }.getOrDefault(updateModeState)
            ComposeFrameDriver.updateMode = updateModeState
        }.isSuccess
        if (ok) loaded = true
    }

    private fun save() {
        val file = configFile() ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties()
            if (file.isFile) file.inputStream().use { props.load(it) }
            props.setProperty(KEY, idState.id)
            props.setProperty(ENGINE_KEY, engineIdState)
            props.setProperty(STYLE_KEY, styleIdState)
            props.setProperty(UPDATE_MODE_KEY, updateModeState.name)
            file.outputStream().use { props.store(it, "$MODID client") }
        }
    }

    private fun configFile() = runCatching {
        Minecraft.getInstance().gameDirectory.resolve("config").resolve("$MODID-client.properties")
    }.getOrNull()
}
