package allyouneed.util

import allyouneed.api.AsyncChannelNodeHolder
import allyouneed.api.BigCpuCapacity
import allyouneed.api.GlobalIdHolder
import allyouneed.api.IMacAddressHolder
import appeng.api.stacks.AEKey
import appeng.api.upgrades.IUpgradeableItem
import appeng.api.upgrades.Upgrades
import appeng.core.definitions.ItemDefinition
import appeng.me.GridNode
import appeng.me.cluster.implementations.CraftingCPUCluster

fun IUpgradeableItem.registerSupportedUpgrade(item: ItemDefinition<*>, max: Int = 1, tooltipGroup: String? = null) {
    Upgrades.add(item, this, max, tooltipGroup)
}

class RegisterSupportedUpgrade internal constructor(
    private val upgradeable: IUpgradeableItem,
    private val tooltipGroup: String?,
) {
    fun with(vararg items: Pair<ItemDefinition<*>, Int>) {
        items.forEach { upgradeable.registerSupportedUpgrade(it.first, it.second, tooltipGroup) }
    }
}

fun IUpgradeableItem.registerSupportedUpgrade(tooltipGroup: String? = null) =
    RegisterSupportedUpgrade(this, tooltipGroup)

var GridNode.asyncSwallowedChannels
    get() = (this as AsyncChannelNodeHolder).asyncSwallowedChannels
    set(value) {
        (this as AsyncChannelNodeHolder).asyncSwallowedChannels = value
    }

var CraftingCPUCluster.bigStorage
    get() = (this as BigCpuCapacity).bigStorage
    set(value) {
        (this as BigCpuCapacity).bigStorage = value
    }

var CraftingCPUCluster.isUnboundedCapacity
    get() = (this as BigCpuCapacity).isUnboundedCapacity
    set(value) {
        (this as BigCpuCapacity).isUnboundedCapacity = value
    }

val AEKey.globalId get() = (this as GlobalIdHolder).globalId

fun AEKey.invalidateGlobalId() = (this as GlobalIdHolder).invalidateGlobalId()

val GridNode.macAddress get() = (this as IMacAddressHolder).macAddress
