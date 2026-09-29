package allyouneed.item.packet

import allyouneed.logic.aekey.*
import allyouneed.util.rl
import allyouneed.util.satMul
import appeng.api.config.Actionable
import appeng.api.stacks.AEKey
import appeng.api.stacks.AEKeyType
import appeng.api.stacks.AEKeyTypes
import appeng.core.MainCreativeTab
import appeng.core.definitions.ItemDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import kotlin.math.min

/**
 * 封包物品注册中心。
 *
 * 封包类型直接用 AEKeyType 的 ResourceLocation 作为 tag 值（如 `t: "ae2:f"`），
 * 内容为 key 自身的 `toTag` 数据；任何已注册的 AEKeyType 都可被封包，无需逐类型硬编码。
 * NBT 格式：`t`=KeyType id，`k`=key 数据，`amt`=数量。
 *
 * Packet item registry.
 * The packet type tag is the AEKeyType's own ResourceLocation (e.g. `t: "ae2:f"`), with the
 * key's `toTag` payload alongside; any registered AEKeyType can be packetized without
 * per-type hardcoding. NBT format: `t`=key type id, `k`=key data, `amt`=amount.
 */
object AllPackets {
    const val TAG_TYPE = "t"
    const val TAG_KEY = "k"
    const val TAG_AMOUNT = "amt"

    lateinit var packet: ItemDefinition<PacketItem>; private set

    val all: List<ItemDefinition<*>>
        get() = listOf(packet)

    fun init() {
        packet = register("packet", ::PacketItem)

        all.forEach { MainCreativeTab.add(it) }
    }

    private fun <T : Item> register(path: String, factory: () -> T): ItemDefinition<T> {
        return ItemDefinition(path, path.rl, factory())
    }

    fun isPacket(stack: ItemStack): Boolean {
        return !stack.isEmpty && stack.tag?.contains(TAG_TYPE) == true
    }

    fun getResourceType(stack: ItemStack): String? = stack.tag?.getString(TAG_TYPE)

    fun toAEKey(stack: ItemStack): AEKey? {
        val tag = stack.tag ?: return null
        val typeId = tag.getString(TAG_TYPE)
        if (typeId.isEmpty()) return null
        val type = try {
            AEKeyTypes.get(ResourceLocation(typeId))
        } catch (e: Exception) {
            return null
        }
        return type.loadKeyFromTag(tag.getCompound(TAG_KEY))
    }

    fun getResourceAmount(stack: ItemStack): Long = stack.tag?.getLong(TAG_AMOUNT) ?: 0L

    fun createPacket(key: AEKey, amount: Long): ItemStack {
        val stack = ItemStack(packet.asItem())
        val tag = stack.orCreateTag
        tag.putString(TAG_TYPE, key.type.id.toString())
        tag.put(TAG_KEY, key.toTag())
        tag.putLong(TAG_AMOUNT, amount)
        return stack
    }

    /**
     * 模型 override 索引：与 resgen 生成的 packet 模型 `ae2isallyouneed:type` 谓词值对应。
     * ItemProperties.register 只接受 ClampedItemPropertyFunction（钳制到 [0,1]），
     * 因此用 0.125 步长的分数值（二进制浮点下精确）。
     * 图标纹理是构建期静态资源，未列入的 KeyType 回退为基础图标。
     */
    private val ICON_VALUES: Map<String, Float> by lazy {
        mapOf(
            AEKeyType.items().id.toString() to 0.125f,
            AEKeyType.fluids().id.toString() to 0.25f,
            EnergyKey.Type.id.toString() to 0.375f,
            ManaKey.Type.id.toString() to 0.5f,
            HpKey.Type.id.toString() to 0.625f,
            StaKey.Type.id.toString() to 0.75f,
            XpKey.Type.id.toString() to 0.875f,
        )
    }

    fun modelIndex(stack: ItemStack): Float {
        val type = getResourceType(stack) ?: return 0f
        return ICON_VALUES[type] ?: 0f
    }

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

        val total = perItem satMul attempt.toLong()
        val simulated = inserter.insert(key, total, Actionable.SIMULATE).coerceAtLeast(0L)
        val complete = min(simulated / perItem, attempt.toLong())
        if (complete <= 0L) return stack
        if (!simulate) {
            inserter.insert(key, perItem satMul complete, Actionable.MODULATE)
        }
        val leftoverCount = stack.count - complete
        if (leftoverCount <= 0L) return ItemStack.EMPTY
        val leftover = stack.copy()
        leftover.count = leftoverCount.toInt()
        return leftover
    }
}
