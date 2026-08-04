package allyouneed.netaddr.mac

import allyouneed.util.MODID
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.collections.iterator

object MacTooltipTexts {
    const val SERVER_TAG = "ayn_macs"
    private const val LANG_MAC = "gui.$MODID.mac"
    private const val LANG_MAC_NAMED = "gui.$MODID.mac_named"
    private const val LANG_MAC_ITEM = "gui.$MODID.mac_item"
    private val DEFAULT_TAG_NAMES = setOf("proxy", "gn")

    @JvmStatic
    fun writeServerData(serverData: CompoundTag, macs: Map<String, Long>) {
        if (macs.isEmpty()) {
            serverData.remove(SERVER_TAG)
            return
        }
        val compound = CompoundTag()
        for ((name, mac) in macs) {
            if (MacAddress.isValid(mac)) {
                compound.putLong(name, MacAddress.normalize(mac))
            }
        }
        if (compound.isEmpty) {
            serverData.remove(SERVER_TAG)
        } else {
            serverData.put(SERVER_TAG, compound)
        }
    }

    @JvmStatic
    fun readServerData(serverData: CompoundTag): Map<String, Long> {
        if (!serverData.contains(SERVER_TAG, Tag.TAG_COMPOUND.toInt())) return emptyMap()
        val compound = serverData.getCompound(SERVER_TAG)
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
    fun linesFromMacs(macs: Map<String, Long>): List<Component> {
        if (macs.isEmpty()) return emptyList()
        val lines = ArrayList<Component>(macs.size)
        val singleDefault = macs.size == 1 && macs.keys.single() in DEFAULT_TAG_NAMES
        for ((name, mac) in macs) {
            val formatted = MacAddress.format(mac)
            val line = if (singleDefault) {
                Component.translatable(LANG_MAC, formatted)
            } else {
                Component.translatable(LANG_MAC_NAMED, displayTagName(name), formatted)
            }
            lines.add(line.withStyle(ChatFormatting.DARK_AQUA))
        }
        return lines
    }

    @JvmStatic
    fun appendItemTooltip(stack: ItemStack, lines: MutableList<Component>) {
        val macs = MacNbt.readFromStack(stack)
        if (macs.isEmpty()) return
        for ((name, mac) in macs) {
            val formatted = MacAddress.format(mac)
            val line = if (macs.size == 1 && name in DEFAULT_TAG_NAMES) {
                Component.translatable(LANG_MAC_ITEM, formatted)
            } else {
                Component.translatable(LANG_MAC_NAMED, displayTagName(name), formatted)
            }
            lines.add(line.withStyle(ChatFormatting.DARK_AQUA))
        }
    }

    private fun displayTagName(tagName: String): String = when (tagName) {
        "proxy", "gn" -> "main"
        else -> tagName
    }
}
