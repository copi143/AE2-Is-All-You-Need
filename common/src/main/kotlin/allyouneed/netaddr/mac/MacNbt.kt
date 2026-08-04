package allyouneed.netaddr.mac

import allyouneed.util.MODID
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import kotlin.collections.iterator

/**
 * Item / settings NBT helpers for MAC export on wrench dismantle.
 *
 * Layout on item stacks:
 * ```
 * allyouneed_macs: {
 *   "proxy": <long>,
 *   "gn": <long>,
 *   "outer": <long>
 * }
 * ```
 * Keys are [appeng.me.ManagedGridNode] tag names.
 */
object MacNbt {
    const val ITEM_TAG = "${MODID}_macs"
    const val NODE_KEY = "ayn_mac"

    @JvmStatic
    fun writeNodeMac(nodeTag: CompoundTag, mac: Long) {
        if (MacAddress.isValid(mac)) {
            nodeTag.putLong(NODE_KEY, mac)
        } else {
            nodeTag.remove(NODE_KEY)
        }
    }

    @JvmStatic
    fun readNodeMac(nodeTag: CompoundTag?): Long {
        if (nodeTag == null || !nodeTag.contains(NODE_KEY)) return MacAddress.NONE
        return nodeTag.getLong(NODE_KEY)
    }

    @JvmStatic
    fun putMacs(tag: CompoundTag, macs: Map<String, Long>) {
        if (macs.isEmpty()) {
            tag.remove(ITEM_TAG)
            return
        }
        val compound = CompoundTag()
        for ((name, mac) in macs) {
            if (MacAddress.isValid(mac)) {
                compound.putLong(name, mac)
            }
        }
        if (compound.isEmpty) {
            tag.remove(ITEM_TAG)
        } else {
            tag.put(ITEM_TAG, compound)
        }
    }

    @JvmStatic
    fun getMacs(tag: CompoundTag?): Map<String, Long> {
        if (tag == null || !tag.contains(ITEM_TAG)) return emptyMap()
        val compound = tag.getCompound(ITEM_TAG)
        if (compound.isEmpty) return emptyMap()
        val result = LinkedHashMap<String, Long>()
        for (key in compound.allKeys) {
            val mac = MacAddress.normalize(compound.getLong(key))
            if (MacAddress.isValid(mac)) {
                result[key] = mac
            }
        }
        return result
    }

    @JvmStatic
    fun stripMacs(tag: CompoundTag?) {
        tag?.remove(ITEM_TAG)
    }

    @JvmStatic
    fun writeToStack(stack: ItemStack, macs: Map<String, Long>) {
        if (macs.isEmpty()) {
            stack.tag?.let { stripMacs(it) }
            return
        }
        putMacs(stack.orCreateTag, macs)
    }

    @JvmStatic
    fun readFromStack(stack: ItemStack): Map<String, Long> = getMacs(stack.tag)
}
