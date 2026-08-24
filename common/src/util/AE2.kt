@file:Suppress("CAST_NEVER_SUCCEEDS")

/**
 * Mixin 添加的接口实现 IDE 无法获取到，所以全部在这里注册扩展函数然后抑制警告
 */

package allyouneed.util

import allyouneed.api.AsyncChannelNodeHolder
import allyouneed.api.BigCpuCapacity
import allyouneed.api.IMacAddressHolder
import allyouneed.api.IManagedMacAddressHolder
import allyouneed.logic.aekey.*
import appeng.api.stacks.AEKey
import appeng.api.stacks.AEKeyType
import appeng.api.upgrades.IUpgradeableItem
import appeng.api.upgrades.Upgrades
import appeng.core.definitions.ItemDefinition
import appeng.me.GridNode
import appeng.me.ManagedGridNode
import appeng.me.cluster.implementations.CraftingCPUCluster

val AEKeyType.isItem: Boolean get() = this == AEKeyType.items()
val AEKeyType.isFluid: Boolean get() = this == AEKeyType.fluids()
val AEKeyType.isEnergy: Boolean get() = this == EnergyKey.Type
val AEKeyType.isMana: Boolean get() = this == ManaKey.Type
val AEKeyType.isHp: Boolean get() = this == HpKey.Type
val AEKeyType.isSta: Boolean get() = this == StaKey.Type
val AEKeyType.isXp: Boolean get() = this == XpKey.Type

val AEKey.isItem: Boolean get() = this.type.isItem
val AEKey.isFluid: Boolean get() = this.type.isFluid
val AEKey.isEnergy: Boolean get() = this.type.isEnergy
val AEKey.isMana: Boolean get() = this.type.isMana
val AEKey.isHp: Boolean get() = this.type.isHp
val AEKey.isSta: Boolean get() = this.type.isSta
val AEKey.isXp: Boolean get() = this.type.isXp

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

val AEKey.droppedSecondary: AEKey get() = dropSecondary()

val GridNode.macAddress get() = (this as IMacAddressHolder).macAddress

val ManagedGridNode.macAddress get() = (this as IManagedMacAddressHolder).macAddress
