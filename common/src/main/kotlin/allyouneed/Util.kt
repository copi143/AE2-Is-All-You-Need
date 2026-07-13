package allyouneed

import net.minecraft.resources.ResourceLocation

val String.rl get() = ResourceLocation(Constants.MOD_ID, this)

/**
 * @return [ResourceLocation] from the string using the vanilla namespace
 */
fun String.vanillaLocation() = ResourceLocation(this)
