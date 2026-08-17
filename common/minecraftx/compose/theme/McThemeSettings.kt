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
    private var loaded = false

    private var idState by mutableStateOf(McThemeId.Dark)

    val id: McThemeId
        get() {
            ensureLoaded()
            return idState
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
        loaded = true
        val file = configFile() ?: return
        if (!file.isFile) return
        runCatching {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            idState = McThemeId.fromId(props.getProperty(KEY))
        }
    }

    private fun save() {
        val file = configFile() ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties()
            props.setProperty(KEY, idState.id)
            file.outputStream().use { props.store(it, "$MODID client") }
        }
    }

    private fun configFile() = runCatching {
        Minecraft.getInstance().gameDirectory.resolve("config").resolve("$MODID-client.properties")
    }.getOrNull()
}
