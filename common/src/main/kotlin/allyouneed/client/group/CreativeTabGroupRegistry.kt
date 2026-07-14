package allyouneed.client.group

import allyouneed.Constants
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object CreativeTabGroupRegistry {
    val ALL_GROUP_ID = ResourceLocation(Constants.MOD_ID, "all")

    private val groups = LinkedHashMap<ResourceLocation, CreativeTabGroup>()
    private var selectedGroupId: ResourceLocation = ALL_GROUP_ID

    val allGroup: CreativeTabGroup by lazy {
        CreativeTabGroup(
            ALL_GROUP_ID,
            Component.translatable("gui.${Constants.MOD_ID}.group.all"),
            { ItemStack(Items.CRAFTING_TABLE) }
        )
    }

    init {
        groups[ALL_GROUP_ID] = allGroup
    }

    fun register(group: CreativeTabGroup): CreativeTabGroup {
        groups[group.id] = group
        return group
    }

    fun addTabToGroup(tabId: ResourceLocation, groupId: ResourceLocation) {
        val group = groups.getOrPut(groupId) {
            CreativeTabGroup(groupId, Component.literal(groupId.toString()), { ItemStack(Items.BARRIER) })
        }
        group.tabIds.add(tabId)
    }

    fun getGroup(groupId: ResourceLocation): CreativeTabGroup? = groups[groupId]

    fun getGroups(): Map<ResourceLocation, CreativeTabGroup> = groups

    fun getGroupList(): List<CreativeTabGroup> = groups.values.toList()

    fun getSelectedGroupId(): ResourceLocation = selectedGroupId

    fun setSelectedGroup(groupId: ResourceLocation) {
        if (groups.containsKey(groupId)) {
            selectedGroupId = groupId
        }
    }

    fun getSelectedGroup(): CreativeTabGroup = groups[selectedGroupId] ?: allGroup

    fun isTabInSelectedGroup(tabId: ResourceLocation): Boolean {
        val selected = getSelectedGroup()
        if (selected.id == ALL_GROUP_ID) return true
        return selected.tabIds.contains(tabId)
    }

    fun getVisibleTabIds(): Set<ResourceLocation> {
        val selected = getSelectedGroup()
        if (selected.id == ALL_GROUP_ID) return emptySet()
        return selected.tabIds.toSet()
    }
}
