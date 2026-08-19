package allyouneed.gtceu

import allyouneed.logic.aekey.EnergyType
import appeng.api.config.AccessRestriction
import appeng.api.config.Actionable
import appeng.api.config.PowerMultiplier
import appeng.api.networking.IGrid
import appeng.api.networking.energy.IAEPowerStorage
import appeng.api.networking.events.GridPowerStorageStateChanged
import com.gregtechceu.gtceu.api.GTValues
import com.gregtechceu.gtceu.api.capability.recipe.IO
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer
import com.gregtechceu.gtceu.api.recipe.GTRecipe
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder
import com.gregtechceu.gtceu.integration.ae2.utils.SerializableManagedGridNode
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder
import net.minecraft.core.Direction
import net.minecraft.server.TickTask
import net.minecraft.server.level.ServerLevel
import java.util.*
import kotlin.math.ceil

/**
 * AE 动力仓：GTCEu 多方块的输出能量仓（dynamo hatch）。内部 EU 缓冲由多方块注入；AE 侧
 * 不再主动充能，而是把自己注册为网格节点服务 [IAEPowerStorage]（`READ` 流向），AE 网络
 * 按需通过 [extractAEPower] 直接抽取仓内能量。网格节点始终把前脸（frontFacing）作为暴露面
 * 接入附近线缆。只要仓内缓存非空就向 AE 供电，成形与否不影响（红石/工作禁用除外）：
 * 世界重载后首个网格 tick 即可抽到电，供电零断档。
 *
 * 注意：结构事件（addedToController/removedFromController）期间绝不能触碰网格节点或
 * 翻转方块状态——`setExposedOnSides` 会触发 AE2 路径重算，`setBlock` 会重入 GTCEu 的
 * 多方块检查，二者都会崩溃。这里与 GTCEu 自带 `MEHatchPartMachine` 保持一致：暴露面
 * 只在旋转（[onRotated]）时更新。
 *
 * AE power hatch: a GTCEu multiblock output energy hatch. The internal EU buffer is fed by the
 * multiblock; on the AE side the machine does not actively push power any more - it registers
 * itself as an [IAEPowerStorage] grid-node service (`READ` flow) and the AE network draws the
 * stored energy on demand through [extractAEPower]. The grid node keeps the front face exposed
 * for cable connections. As long as the buffer holds EU the hatch feeds the AE grid regardless
 * of multiblock form (except when disabled by redstone/work toggle); the first grid tick after a
 * world reload can therefore draw power with no outage.
 *
 * Note: never touch the grid node or flip blockstates during structure events
 * (addedToController/removedFromController) - `setExposedOnSides` triggers an AE2 pathing
 * recompute and `setBlock` re-enters GTCEu's multiblock check, both crash. This matches the
 * stock `MEHatchPartMachine`: exposed sides are only updated on rotation ([onRotated]).
 */
class AEPowerHatchMachine(holder: IMachineBlockEntity, tier: Int, amperage: Int) :
    TieredIOPartMachine(holder, tier, IO.OUT), IGridConnectedMachine, IAEPowerStorage {

    @Persisted
    private val energyContainer: AEPowerHatchEnergyContainer = AEPowerHatchEnergyContainer(this, tier, amperage)

    @Persisted
    private val nodeHolder: AEPowerHatchGridNodeTrait = AEPowerHatchGridNodeTrait(this)

    private var online = false

    /** 防抖：同一 server 任务只排一次队，避免能量频繁变化时刷屏。 */
    private var powerNotifyQueued = false

    // ----------------------------------------------------------------------------------------------------
    // IGridConnectedMachine
    // ----------------------------------------------------------------------------------------------------

    override fun getMainNode(): SerializableManagedGridNode = nodeHolder.mainNode
    override fun isOnline(): Boolean = online
    override fun setOnline(online: Boolean) {
        this.online = online
    }

    // ----------------------------------------------------------------------------------------------------
    // TieredIOPartMachine
    // ----------------------------------------------------------------------------------------------------

    override fun getFieldHolder(): ManagedFieldHolder = managedFieldHolder

    companion object {
        val eu2ae = EnergyType.ratioOf(EnergyType.GtceuEu to EnergyType.AE)
        val ae2eu = EnergyType.ratioOf(EnergyType.AE to EnergyType.GtceuEu)

        private val managedFieldHolder = ManagedFieldHolder(
            AEPowerHatchMachine::class.java,
            MANAGED_FIELD_HOLDER,
        )
    }

    /**
     * 成形时兜底补发一次 [GridPowerStorageStateChanged](PROVIDE_POWER) 事件，把本仓重新注册为
     * 能量提供者。AE2 的 `EnergyService.extractProviderPower` 在 `MODULATE` 抽取时，只要某个
     * 提供者一次满足不了整网的空闲功耗（`newPower < req`）就会把它从提供者集合里移除；仓内
     * 能量耗空后不会被自动重新识别，重新注册只有节点重连或 PROVIDE_POWER 事件两条路，本事件
     * 是公开的兜底通道（结构事件期间不能直接触碰网格节点，故排队到下一个 server 任务再发送）。
     *
     * Re-register this hatch as an [IAEPowerStorage] provider on (re-)form by posting a
     * [GridPowerStorageStateChanged] PROVIDE_POWER event. AE2's `EnergyService` drops a provider
     * from its set whenever one `MODULATE` draw cannot cover the whole network's idle draw
     * (`newPower < req`); a drained hatch is never re-discovered on its own, and re-registration
     * only happens via a node reconnect or a PROVIDE_POWER event. Since the grid node must not be
     * touched during structure events, the notification is queued as a server task instead.
     */
    override fun addedToController(controller: IMultiController) {
        super.addedToController(controller)
        queuePowerNotify()
    }

    internal fun postEvent(grid: IGrid) {
        grid.postEvent(GridPowerStorageStateChanged(this, GridPowerStorageStateChanged.PowerEventType.PROVIDE_POWER))
    }

    internal fun queuePowerNotify() {
        if (powerNotifyQueued) return
        if (energyContainer.energyStored <= 0) return
        val lvl = level as? ServerLevel ?: return
        powerNotifyQueued = true
        lvl.server.tell(TickTask(0) {
            powerNotifyQueued = false
            if (!isInValid && energyContainer.energyStored > 0) {
                nodeHolder.mainNode.ifPresent(::postEvent)
            }
        })
    }

    override fun setWorkingEnabled(workingEnabled: Boolean) {
        super.setWorkingEnabled(workingEnabled)
        if (workingEnabled) {
            queuePowerNotify()
        }
    }

    override fun onRotated(oldFacing: Direction, newFacing: Direction) {
        super.onRotated(oldFacing, newFacing)
        nodeHolder.mainNode.setExposedOnSides(EnumSet.of(newFacing))
    }

    override fun tintColor(index: Int): Int = if (index == 2) GTValues.VC[tier] else super.tintColor(index)

    // ----------------------------------------------------------------------------------------------------
    // IAEPowerStorage
    // ----------------------------------------------------------------------------------------------------

    override fun extractAEPower(amt: Double, mode: Actionable, usePowerMultiplier: PowerMultiplier): Double {
        val level = level ?: return 0.0
        if (level.isClientSide) return 0.0
        if (!isWorkingEnabled) return 0.0
        if (amt <= 0.0) return 0.0

        val requestedEu = ceil(amt * ae2eu).toLong()
        if (requestedEu <= 0) return 0.0

        val extractEu = minOf(energyContainer.energyStored, requestedEu)
        if (extractEu <= 0) return 0.0

        if (mode == Actionable.MODULATE) {
            energyContainer.setEnergyStored(energyContainer.energyStored - extractEu)
        }
        return extractEu * eu2ae
    }

    override fun injectAEPower(amt: Double, mode: Actionable): Double = amt
    override fun getAEMaxPower(): Double = energyContainer.energyCapacity * eu2ae
    override fun getAECurrentPower(): Double = energyContainer.energyStored * eu2ae
    override fun isAEPublicPowerStorage(): Boolean = true
    override fun getPowerFlow(): AccessRestriction = AccessRestriction.READ
    override fun getPriority(): Int = 1 shl 30
}

/**
 * 能量容器：纯缓冲。多方块产出的 EU 经 [handleRecipeInner] 注入，
 * 之后只被 AE 侧 [AEPowerHatchMachine.extractAEPower] 抽走，不再主动向任何方向输出 EU，
 * 因此 [serverTick] 覆写为空实现。
 *
 * GT 侧注入按额定输出限速：每个 tick 最多接收 `outputVoltage × outputAmperage` EU
 * （[handleRecipeInner] 与 [changeEnergy] 双入口统一限速，保证 recipe 匹配与执行一致），
 * 超出额定部分退回 recipe，发电机随之堵转。AE 侧仍可任意速率读取缓存，缓存水位
 * 只能以额定输出速率上升。
 *
 * Energy container: a pure buffer. EU produced by the multiblock flows in through
 * [handleRecipeInner] and is only drawn by the AE side via
 * [AEPowerHatchMachine.extractAEPower]; it no longer pushes EU anywhere, so [serverTick] is
 * overridden with an empty body to suppress the base GT energy output.
 *
 * The GT-side input is rate-limited to the rated output (`outputVoltage × outputAmperage` EU per
 * tick, enforced identically in [handleRecipeInner] and [changeEnergy] so recipe matching and
 * execution stay consistent); anything above the rated rate is returned to the recipe and backs
 * up the generator. The AE side still reads the buffer at any rate, so the buffer only fills as
 * fast as the rated output.
 */
class AEPowerHatchEnergyContainer(
    val machine: AEPowerHatchMachine,
    tier: Int,
    amperage: Int,
) : NotifiableEnergyContainer(
    machine,
    GTValues.V[tier] * 64L * amperage,
    0,
    0,
    GTValues.V[tier],
    amperage.toLong(),
) {

    /** 本 tick 剩余可注入的 EU 预算，跨 tick 在 [refreshInputBudget] 重置为额定输出。 */
    private var inputBudget = 0L

    /** 上次刷新预算的 tick 时间戳；与 [com.gregtechceu.gtceu.api.machine.MetaMachine.getOffsetTimer] 不同则重置预算。 */
    private var lastInputTick = Long.MIN_VALUE

    private val ratedOutputPerTick: Long
        get() = outputVoltage * outputAmperage

    private fun refreshInputBudget() {
        val tick = machine.offsetTimer
        if (tick != lastInputTick) {
            lastInputTick = tick
            inputBudget = ratedOutputPerTick
        }
    }

    override fun serverTick() {
        // EU only leaves the hatch through AE's IAEPowerStorage extraction.
    }

    /**
     * 注入入口限速（覆盖 `addEnergy` 等非 recipe 路径）：正的 [energyToAdd] 最多注入本 tick
     * 剩余预算。AE 侧抽取走 [setEnergyStored]，不经此处，故不受限速影响。
     */
    override fun changeEnergy(energyToAdd: Long): Long {
        if (energyToAdd <= 0) return super.changeEnergy(energyToAdd)
        refreshInputBudget()
        val accepted = super.changeEnergy(minOf(energyToAdd, inputBudget))
        inputBudget -= accepted
        return accepted
    }

    /**
     * recipe 注入入口限速：`IO.OUT` 时把 `canTransfer` 封顶到本 tick 剩余预算，并返回未注入
     * 的剩余 [EnergyStack]，让 simulate 与 execute 保持一致（否则发电机在匹配时认为能注入
     * 全部、执行时又被限速，差额能量会凭空消失）。`IO.IN` 走父类原逻辑。
     */
    override fun handleRecipeInner(
        io: IO,
        recipe: GTRecipe,
        left: MutableList<EnergyStack>,
        simulate: Boolean,
    ): MutableList<EnergyStack>? {
        if (io != IO.OUT) return super.handleRecipeInner(io, recipe, left, simulate)
        refreshInputBudget()
        val it = left.listIterator()
        while (it.hasNext()) {
            val stack = it.next()
            if (stack.isEmpty) {
                it.remove()
                continue
            }
            val totalEU = stack.totalEU
            val canTransfer = minOf(totalEU, energyCapacity - energyStored, inputBudget)
            if (!simulate) {
                super.changeEnergy(canTransfer)
                inputBudget -= canTransfer
            }
            val remaining = totalEU - canTransfer
            if (remaining <= 0) it.remove() else it.set(EnergyStack(remaining))
        }
        return if (left.isEmpty()) null else left
    }

    /**
     * 每次能量增加（多方块注入 EU）都补发一次 PROVIDE_POWER 事件。仓可能因任何一次
     * `extractAEPower` 未满足整网空闲功耗而被动从 AE2 提供者集合移除（见
     * [AEPowerHatchMachine.addedToController] 的注释），能量回升后必须重新注册，否则网络
     * 持续断电。AE2 抽取导致的能量减少不会触发（`increased == false`）。
     */
    override fun setEnergyStored(energyStored: Long) {
        val increased = energyStored > getEnergyStored()
        super.setEnergyStored(energyStored)
        if (increased) {
            machine.queuePowerNotify()
        }
    }
}

/**
 * 为 AE 动力仓配置的 [GridNodeHolder]：节点不占频道（无 REQUIRE_CHANNEL）、无闲置功耗，
 * 并把机器自身注册为 [IAEPowerStorage] 节点服务（`addService` 在节点创建时写入
 * `GridNode.services`，AE2 的 `EnergyService` 由此把它识别为能量提供者）。暴露面沿用基类
 * 默认值（前脸），由旋转更新。初始值之外不做任何生命周期驱动。
 *
 * [GridNodeHolder] configured for the AE power hatch: the node takes no channels (no
 * REQUIRE_CHANNEL), has no idle power draw, and registers the machine itself as an
 * [IAEPowerStorage] node service (`addService` writes it into `GridNode.services` on creation,
 * which AE2's `EnergyService` uses to discover the power provider). Exposed sides keep the base
 * default (front face), updated only on rotation.
 */
class AEPowerHatchGridNodeTrait(machine: IGridConnectedMachine) : GridNodeHolder(machine) {
    override fun createManagedNode(): SerializableManagedGridNode {
        return super.createManagedNode().apply {
            setFlags()
            idlePowerUsage = 0.0
            addService(IAEPowerStorage::class.java, machine as AEPowerHatchMachine)
        }
    }
}
