package allyouneed

import allyouneed.util.MODID
import net.minecraft.resources.ResourceLocation

val String.rl get() = ResourceLocation(MODID, this)

/**
 * @return [ResourceLocation] from the string using the vanilla namespace
 */
fun String.vanillaLocation() = ResourceLocation(this)
