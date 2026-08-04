package allyouneed.async

import net.minecraft.core.BlockPos

/**
 * 一个已成形的 async 合成模块：搭建在模块接口（Z）上的合法 3x7x5 工厂结构，
 * 接口嵌在交换机或处理器的地板里。
 *
 * A formed async synthesis module: a valid 3x7x5 factory structure mounted on a module interface
 * (Z) embedded in the floor of a switch or the processor.
 */
class AsyncModuleCluster(
    val factoryPos: BlockPos,
    val interfacePos: BlockPos,
    val boundsMin: BlockPos,
    val boundsMax: BlockPos,
    val blockCount: Int,
) {
    override fun equals(other: Any?): Boolean = other is AsyncModuleCluster && other.factoryPos == factoryPos
    override fun hashCode(): Int = factoryPos.hashCode()
}

/**
 * 一台已成形的 async 合成网络交换机，以及其扩展舱位上搭建的模块。
 *
 * A formed async synthesis network switch and the modules mounted on its extension bays.
 */
class AsyncSwitchCluster(
    val anchorPos: BlockPos,
    val boundsMin: BlockPos,
    val boundsMax: BlockPos,
    val blockCount: Int,
    val meConnectorPositions: List<BlockPos> = emptyList(),
    val wanConnectorPositions: List<BlockPos> = emptyList(),
    val lanConnectorPositions: List<BlockPos> = emptyList(),
    val interfacePositions: List<BlockPos> = emptyList(),
) {
    private val modules = ArrayList<AsyncModuleCluster>()
    private var destroyed = false

    val isDestroyed: Boolean get() = destroyed

    val connectorPositions: List<BlockPos>
        get() = meConnectorPositions + wanConnectorPositions + lanConnectorPositions

    fun addModule(module: AsyncModuleCluster) {
        if (!modules.contains(module)) {
            modules.add(module)
        }
    }

    fun clearModules() {
        modules.clear()
    }

    fun getModules(): List<AsyncModuleCluster> = modules

    fun getModuleFactoryPositions(): List<BlockPos> = modules.map { it.factoryPos }

    fun boundsContain(pos: BlockPos): Boolean =
        pos.x in boundsMin.x..boundsMax.x && pos.y in boundsMin.y..boundsMax.y && pos.z in boundsMin.z..boundsMax.z

    fun destroy() {
        destroyed = true
        modules.clear()
    }
}

/**
 * 一个 async 合成网络：一台已成形的处理器，加上搭建在它上面的模块，以及经线缆
 * 与之相连的交换机（含其模块）。大多数网络操作都从这里发起。
 *
 * The async synthesis network: a formed processor plus the modules mounted on it and the switches
 * (and their modules) linked to it. Most network operations run from here.
 */
class AsyncProcessorCluster(
    val anchorPos: BlockPos,
    val boundsMin: BlockPos,
    val boundsMax: BlockPos,
    val blockCount: Int,
    val storageBytes: Long,
    val connectorCount: Int,
    val meConnectorPositions: List<BlockPos> = emptyList(),
    val wanConnectorPositions: List<BlockPos> = emptyList(),
    val lanConnectorPositions: List<BlockPos> = emptyList(),
    val interfacePositions: List<BlockPos> = emptyList(),
) {
    private val modules = ArrayList<AsyncModuleCluster>()
    private val switches = ArrayList<AsyncSwitchCluster>()
    private var destroyed = false

    val isDestroyed: Boolean get() = destroyed

    val connectorPositions: List<BlockPos>
        get() = meConnectorPositions + wanConnectorPositions + lanConnectorPositions

    fun addModule(module: AsyncModuleCluster) {
        if (!modules.contains(module)) {
            modules.add(module)
        }
    }

    fun clearModules() {
        modules.clear()
    }

    fun getModules(): List<AsyncModuleCluster> = modules

    fun getModuleFactoryPositions(): List<BlockPos> = modules.map { it.factoryPos }

    fun addSwitch(sw: AsyncSwitchCluster) {
        if (!switches.contains(sw)) {
            switches.add(sw)
        }
    }

    fun clearSwitches() {
        switches.clear()
    }

    fun getSwitches(): List<AsyncSwitchCluster> = switches

    fun getTotalBlockCount(): Int = blockCount + switches.sumOf { it.blockCount } + modules.size + switches.sumOf { it.getModules().size }

    fun boundsContain(pos: BlockPos): Boolean =
        pos.x in boundsMin.x..boundsMax.x && pos.y in boundsMin.y..boundsMax.y && pos.z in boundsMin.z..boundsMax.z

    fun destroy() {
        destroyed = true
        switches.clear()
        modules.clear()
    }
}
