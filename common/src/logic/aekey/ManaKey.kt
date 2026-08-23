package allyouneed.logic.aekey

/**
 * 魔力 AEKey。
 *
 * 代表一种魔力类型及其二级标识（如 Blood Magic 的不同等级、Ars Nouveau 的不同法术类型等）。
 * [metric] 指定魔力系统，[level] 指定二级标识，空字符串表示基础魔力类型。
 *
 * Mana AEKey.
 *
 * Represents a mana type and its secondary identifier (e.g., Blood Magic ritual tier, Ars Nouveau spell type).
 * [metric] specifies the mana system, [level] specifies the secondary identifier; empty string means the base mana type.
 */
data class ManaKey(override val metric: ManaType, override val level: Int = 0) : MetricLevelKey() {
    override fun getType(): Type = Type

    object Type : MetricLevelKey.Type<ManaKey>(
        "mana",
        ManaKey::class,
        ManaType.idMap,
        { metric, level -> require(metric is ManaType); ManaKey(metric, level) },
        amountPerByte = MANA_GRANULARITY * MANA_PER_BYTE,
        amountPerUnit = MANA_GRANULARITY,
        shortTypeId = "m",
        unitSymbol = "AM",
    )

    companion object {
        const val MANA_PER_BYTE = 64

        /**
         * 存储时使用的粒度，即内部整数表示使用的乘数，以便支持非整数的数量
         */
        const val MANA_GRANULARITY = 1024
    }
}
