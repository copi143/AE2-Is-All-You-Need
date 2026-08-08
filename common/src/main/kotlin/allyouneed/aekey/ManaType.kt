package allyouneed.aekey

import kotlin.math.roundToInt

/**
 * AE2 魔力类型，代表不同的魔力系统。
 * 每个实例代表一种可与 ME 网络对接的魔力系统。
 * 通过 [ManaKey] 实例化为具体的魔力键。
 *
 * 需在模组初始化时通过 [appeng.api.stacks.AEKeyTypes.register] 注册到 AE2 的注册表中。
 *
 * AE2 mana type, representing different mana systems.
 * Each instance represents a mana system that can interface with the ME network.
 * Instantiated as [ManaKey] for actual use.
 * Must be registered to AE2's [appeng.api.stacks.AEKeyTypes] during mod initialization.
 */
enum class ManaType(val id: String, val unit: String, val manaPerAM: Double) {
    AM("ae2", "AM", 1.0), //
    BotaniaMana("botania", "Mana", 1.0), //
    BloodMagicLP("bloodmagic", "LP", 1.0), //
    ArsNouveauMana("ars_nouveau", "Mana", 1.0), //
    ;

    val granularity = (ManaKey.MANA_GRANULARITY / manaPerAM).roundToInt()

    override fun toString(): String = id

    companion object {
        @JvmField
        val idMap = entries.associateBy { it.id }
    }
}
