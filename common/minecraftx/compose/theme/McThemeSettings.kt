package minecraftx.compose.theme

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
    private var loaded = false

    private var idState by mutableStateOf(McThemeId.Dark)
    private var engineIdState by mutableStateOf("vanilla")
    private var styleIdState by mutableStateOf(MinimalMcStyle.id)

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
            file.outputStream().use { props.store(it, "$MODID client") }
        }
    }

    private fun configFile() = runCatching {
        Minecraft.getInstance().gameDirectory.resolve("config").resolve("$MODID-client.properties")
    }.getOrNull()
}
