package allyouneed.item.packet

import allyouneed.logic.aekey.*
import allyouneed.util.rl
import appeng.api.config.Actionable
import appeng.api.stacks.AEFluidKey
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.AEKey
import appeng.core.definitions.ItemDefinition
import appeng.core.MainCreativeTab
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import kotlin.math.min

/**
 * 封包物品注册中心。
 *
 * 所有封包物品在此统一管理，提供创建、识别、转换方法。
 * NBT 格式：`t`=类型标记，`amt`=数量，其余字段因类型而异。
 *
 * Packet item registry.
 * Provides creation, identification, and conversion methods.
 * NBT format: `t`=type marker, `amt`=amount; additional fields vary by type.
 */
object AllPackets {
    const val TAG_TYPE = "t"
    const val TAG_AMOUNT = "amt"
    const val TAG_METRIC = "m"
    const val TAG_LEVEL = "l"
    const val TAG_ITEM = "id"
    const val TAG_ITEM_TAG = "it"
    const val TAG_FLUID = "f"
    const val TAG_FLUID_TAG = "ft"

    const val TYPE_ENERGY = "e"
    const val TYPE_MANA = "n"
    const val TYPE_FLUID = "f"
    const val TYPE_ITEM = "i"
    const val TYPE_HP = "hp"
    const val TYPE_STA = "sta"
    const val TYPE_XP = "xp"

    private val ALL_TYPES = setOf(TYPE_ENERGY, TYPE_MANA, TYPE_FLUID, TYPE_ITEM, TYPE_HP, TYPE_STA, TYPE_XP)

    lateinit var energy: ItemDefinition<EnergyPacketItem>; private set
    lateinit var mana: ItemDefinition<ManaPacketItem>; private set
    lateinit var fluid: ItemDefinition<FluidPacketItem>; private set
    lateinit var item: ItemDefinition<ItemPacketItem>; private set
    lateinit var hp: ItemDefinition<HpPacketItem>; private set
    lateinit var sta: ItemDefinition<StaPacketItem>; private set
    lateinit var xp: ItemDefinition<XpPacketItem>; private set

    val all: List<ItemDefinition<*>>
        get() = listOf(energy, mana, fluid, item, hp, sta, xp)

    fun init() {
        energy = register("e_packet", ::EnergyPacketItem)
        mana = register("m_packet", ::ManaPacketItem)
        fluid = register("f_packet", ::FluidPacketItem)
        item = register("i_packet", ::ItemPacketItem)
        hp = register("hp_packet", ::HpPacketItem)
        sta = register("sta_packet", ::StaPacketItem)
        xp = register("xp_packet", ::XpPacketItem)

        all.forEach { MainCreativeTab.add(it) }
    }

    private fun <T : Item> register(path: String, factory: () -> T): ItemDefinition<T> {
        return ItemDefinition(path, path.rl, factory())
    }

    fun isPacket(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val tag = stack.tag ?: return false
        return tag.contains(TAG_TYPE) && tag.getString(TAG_TYPE) in ALL_TYPES
    }

    fun getResourceType(stack: ItemStack): String? = stack.tag?.getString(TAG_TYPE)

    fun toAEKey(stack: ItemStack): AEKey? {
        val tag = stack.tag ?: return null
        val type = tag.getString(TAG_TYPE)
        return when (type) {
            TYPE_ENERGY -> {
                val metric = EnergyType.idMap[tag.getString(TAG_METRIC)] ?: return null
                EnergyKey(metric)
            }
            TYPE_MANA -> {
                val metric = ManaType.idMap[tag.getString(TAG_METRIC)] ?: return null
                ManaKey(metric)
            }
            TYPE_FLUID -> {
                val fluidId = ResourceLocation(tag.getString(TAG_FLUID))
                val fluid = BuiltInRegistries.FLUID.get(fluidId)
                val extraTag = if (tag.contains(TAG_FLUID_TAG)) tag.getCompound(TAG_FLUID_TAG) else null
                AEFluidKey.of(fluid, extraTag)
            }
            TYPE_ITEM -> {
                val itemId = ResourceLocation(tag.getString(TAG_ITEM))
                val item = BuiltInRegistries.ITEM.get(itemId)
                val extraTag = if (tag.contains(TAG_ITEM_TAG)) tag.getCompound(TAG_ITEM_TAG) else null
                AEItemKey.of(item, extraTag)
            }
            TYPE_HP -> HpKey(tag.getInt(TAG_LEVEL))
            TYPE_STA -> StaKey(tag.getInt(TAG_LEVEL))
            TYPE_XP -> XpKey(tag.getInt(TAG_LEVEL))
            else -> null
        }
    }

    fun getResourceAmount(stack: ItemStack): Long = stack.tag?.getLong(TAG_AMOUNT) ?: 0L

    fun interface ResourceInserter {
        fun insert(what: AEKey, amount: Long, mode: Actionable): Long
    }

    /**
     * 将封包内容写入目标存储，返回剩余 ItemStack。
     * 非封包返回 null。
     *
     * 手持数量为 1：允许部分存入并回写剩余 amt。
     * 手持数量 > 1：只存入完整封包，不拆分。
     */
    fun insert(
        stack: ItemStack,
        maxCount: Int,
        simulate: Boolean,
        inserter: ResourceInserter,
    ): ItemStack? {
        if (!isPacket(stack)) return null
        val key = toAEKey(stack) ?: return stack
        val perItem = getResourceAmount(stack)
        if (perItem <= 0L) return stack
        val attempt = min(stack.count, maxCount).coerceAtLeast(0)
        if (attempt <= 0) return stack

        if (stack.count == 1) {
            val mode = if (simulate) Actionable.SIMULATE else Actionable.MODULATE
            val inserted = inserter.insert(key, perItem, mode).coerceIn(0L, perItem)
            val remaining = perItem - inserted
            if (remaining <= 0L) return ItemStack.EMPTY
            if (inserted <= 0L) return stack
            val leftover = stack.copy()
            leftover.count = 1
            leftover.orCreateTag.putLong(TAG_AMOUNT, remaining)
            return leftover
        }

        val total = saturateMul(perItem, attempt.toLong())
        val simulated = inserter.insert(key, total, Actionable.SIMULATE).coerceAtLeast(0L)
        val complete = min(simulated / perItem, attempt.toLong())
        if (complete <= 0L) return stack
        if (!simulate) {
            inserter.insert(key, saturateMul(perItem, complete), Actionable.MODULATE)
        }
        val leftoverCount = stack.count - complete
        if (leftoverCount <= 0L) return ItemStack.EMPTY
        val leftover = stack.copy()
        leftover.count = leftoverCount.toInt()
        return leftover
    }

    private fun saturateMul(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE
        return a * b
    }

    fun createEnergyPacket(metric: EnergyType, amount: Long): ItemStack {
        val stack = ItemStack(energy.asItem())
        val tag = stack.orCreateTag
        tag.putString(TAG_TYPE, TYPE_ENERGY)
        tag.putString(TAG_METRIC, metric.id)
        tag.putLong(TAG_AMOUNT, amount)
        return stack
    }

    fun createManaPacket(metric: ManaType, amount: Long): ItemStack {
        val stack = ItemStack(mana.asItem())
        val tag = stack.orCreateTag
        tag.putString(TAG_TYPE, TYPE_MANA)
        tag.putString(TAG_METRIC, metric.id)
        tag.putLong(TAG_AMOUNT, amount)
        return stack
    }

    fun createFluidPacket(fluidKey: AEFluidKey, amount: Long): ItemStack {
        val stack = ItemStack(fluid.asItem())
        val tag = stack.orCreateTag
        tag.putString(TAG_TYPE, TYPE_FLUID)
        tag.putString(TAG_FLUID, fluidKey.id.toString())
        val fluidTag = fluidKey.tag
        if (fluidTag != null) {
            tag.put(TAG_FLUID_TAG, fluidTag.copy())
        }
        tag.putLong(TAG_AMOUNT, amount)
        return stack
    }

    fun createItemPacket(itemKey: AEItemKey, amount: Long): ItemStack {
        val stack = ItemStack(item.asItem())
        val tag = stack.orCreateTag
        tag.putString(TAG_TYPE, TYPE_ITEM)
        tag.putString(TAG_ITEM, itemKey.id.toString())
        val itemTag = itemKey.tag
        if (itemTag != null) {
            tag.put(TAG_ITEM_TAG, itemTag.copy())
        }
        tag.putLong(TAG_AMOUNT, amount)
        return stack
    }

    fun createLevelPacket(type: String, level: Int, amount: Long): ItemStack {
        val def = when (type) {
            TYPE_HP -> hp
            TYPE_STA -> sta
            TYPE_XP -> xp
            else -> throw IllegalArgumentException("Unknown level packet type: $type")
        }
        val stack = ItemStack(def.asItem())
        val tag = stack.orCreateTag
        tag.putString(TAG_TYPE, type)
        tag.putInt(TAG_LEVEL, level)
        tag.putLong(TAG_AMOUNT, amount)
        return stack
    }
}
