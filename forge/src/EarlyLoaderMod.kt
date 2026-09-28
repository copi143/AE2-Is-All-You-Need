package allyouneed

import allyouneed.util.coreLogger
import net.minecraftforge.fml.common.Mod

@Mod("ae2isallyouneed_core")
class EarlyLoaderMod {
    init {
        CommonMain.beforeAllMods()
        if (System.getProperty("allyouneed.core.transformer") == "true") {
            coreLogger.info("launch plugin installed")
        } else {
            coreLogger.warn("transformer jar missing from mods/; ASM will not rewrite AEKey")
        }
    }
}
