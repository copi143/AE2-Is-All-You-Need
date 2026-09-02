package allyouneed.logic.aekey

import allyouneed.Platform
import allyouneed.util.logger
import kotlin.math.roundToInt

/**
 * AE2 能量类型，代表不同的能量系统。
 * 每个实例代表一种可与 ME 网络对接的能量系统。
 * 通过 [EnergyKey] 实例化为具体的能量键。
 *
 * 需在模组初始化时通过 [appeng.api.stacks.AEKeyTypes.register] 注册到 AE2 的注册表中。
 *
 * AE2 energy type, representing different energy systems.
 * Each instance represents an energy system that can interface with the ME network.
 * Instantiated as [EnergyKey] for actual use.
 * Must be registered to AE2's [appeng.api.stacks.AEKeyTypes] during mod initialization.
 */
enum class EnergyType(
    override val id: String,
    val unit: String,
) : MetricLevelKey.Metric<EnergyKey> {
    AE("ae2", "AE"), //
    ForgeEnergy("forge", "FE"), //
    TechReborn("team_reborn_energy", "E"), //
    GtceuEu("gtceu", "EU"), //
    ;

    val energyPerAE: Double by lazy { if (this == AE) 1.0 else Platform.energyUnitRatio(id) }

    override val typeKey: EnergyKey = EnergyKey(this)

    override val granularity: Int by lazy { (EnergyKey.ENERGY_GRANULARITY / energyPerAE).roundToInt() }

    override fun toString(): String = id

    companion object {
        @JvmField
        val idMap = entries.associateBy { it.id }

        @JvmStatic
        fun ratioOf(pair: Pair<EnergyType, EnergyType>): Double {
            return pair.second.energyPerAE / pair.first.energyPerAE
        }

        init {
            logger.info("Applied Energistics ...")
        }
    }
}
