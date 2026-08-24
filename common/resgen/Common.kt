package allyouneed.resgen

val tiers = listOf(
    "1k", "4k", "16k", "64k", "256k",
    "1m", "4m", "16m", "64m", "256m",
    "1g", "4g", "16g", "64g", "256g",
    "1t", "4t", "16t", "64t", "256t",
).map { it.uppercase() }

val gtMultiBlockTiers = listOf(
    "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV", "MAX",
)

val gtSingleBlockTiers = listOf(
    "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV",
)

val aeKeyLabels = listOf(
    "Item", "Fluid", "Energy", "Mana", "HP", "STA", "XP",
)
