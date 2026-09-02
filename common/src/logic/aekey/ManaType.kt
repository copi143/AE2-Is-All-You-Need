package allyouneed.logic.aekey

import allyouneed.Platform
import allyouneed.util.logger
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
enum class ManaType(
    override val id: String,
    val unit: String,
) : MetricLevelKey.Metric<ManaKey> {
    AM("ae2", "AM"), //
    BotaniaMana("botania", "Mana"), //
    BloodMagicLP("bloodmagic", "LP"), //
    ArsNouveauMana("ars_nouveau", "Mana"), //
    ;

    val manaPerAM: Double by lazy { if (this == AM) 1.0 else Platform.manaUnitRatio(id) }

    override val typeKey: ManaKey = ManaKey(this)

    override val granularity: Int by lazy { (ManaKey.MANA_GRANULARITY / manaPerAM).roundToInt() }

    override fun toString(): String = id

    companion object {
        @JvmField
        val idMap = entries.associateBy { it.id }

        @JvmStatic
        fun ratioOf(pair: Pair<ManaType, ManaType>): Double {
            return pair.second.manaPerAM / pair.first.manaPerAM
        }

        init {
            logger.info("Applied Magicks ...")
        }
    }
}
