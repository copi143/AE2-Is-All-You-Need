package allyouneed.transformer

object KeyClassScanner {
    const val AE_KEY = "appeng/api/stacks/AEKey"
    const val AE_KEY_ASM = "appeng/api/stacks/AEKeyAsm"
    const val AE_ITEM_KEY = "appeng/api/stacks/AEItemKey"
    const val AE_FLUID_KEY = "appeng/api/stacks/AEFluidKey"

    val SEED_KEYS = arrayOf(
        AE_ITEM_KEY,
        AE_FLUID_KEY,
        "allyouneed/logic/aekey/EnergyKey",
        "allyouneed/logic/aekey/ManaKey",
        "allyouneed/logic/aekey/VirtualKey",
    )
}
