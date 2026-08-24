package allyouneed.logic.aekey

/**
 * 能量 AEKey。
 *
 * 代表一种能量类型及其二级标识（如 GTCEu 电压等级、Mekanism 能量储存单元类型等）。
 * [metric] 指定能量系统，[level] 指定二级标识，空字符串表示基础能量类型。
 *
 * Energy AEKey.
 *
 * Represents an energy type and its secondary identifier (e.g., GTCEu voltage tier, Mekanism energy unit type).
 * [metric] specifies the energy system, [level] specifies the secondary identifier; empty string means the base energy type.
 */
data class EnergyKey(override val metric: EnergyType, override val level: Int = 0) : MetricLevelKey() {
    override fun getType(): Type = Type

    object Type : MetricLevelKey.Type<EnergyKey>(
        "energy",
        EnergyKey::class,
        EnergyType.idMap,
        { metric, level -> require(metric is EnergyType); EnergyKey(metric, level) },
        amountPerByte = ENERGY_GRANULARITY * ENERGY_PER_BYTE,
        amountPerUnit = ENERGY_GRANULARITY,
        shortTypeId = "e",
        unitSymbol = "AE",
    )

    companion object {
        /**
         * 每字节存储的能量
         */
        const val ENERGY_PER_BYTE = 64

        /**
         * 存储时使用的粒度，即内部整数表示使用的乘数，以便支持非整数的数量
         */
        const val ENERGY_GRANULARITY = 1024
    }
}
