package allyouneed.forge.early

import allyouneed.Main
import net.minecraftforge.fml.common.Mod

const val CORE_MODID = "ae2isallyouneed_core"

@Mod(CORE_MODID)
class EarlyLoaderMod {
    init {
        Main.beforeAllMods()
        if (System.getProperty("allyouneed.core.transformer") == "true") {
            println("[AE2IsAllYouNeed/Core] launch plugin installed")
        } else {
            println("[AE2IsAllYouNeed/Core] WARN: transformer jar missing from mods/; ASM will not rewrite AEKey")
        }
    }
}
