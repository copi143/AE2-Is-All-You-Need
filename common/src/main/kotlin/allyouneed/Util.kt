package allyouneed

import allyouneed.util.MODID
import net.minecraft.resources.ResourceLocation

fun String.rl(ns: String) = ResourceLocation(ns, this)

val String.rl get() = this.rl(MODID)

val String.rlMC get() = this.rl("minecraft")

val String.rlAE get() = this.rl("ae2")
