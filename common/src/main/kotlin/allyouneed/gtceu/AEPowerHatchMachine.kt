package allyouneed.gtceu

import appeng.api.config.AccessRestriction
import appeng.api.config.Actionable
import appeng.api.config.PowerMultiplier
import appeng.api.config.PowerUnits
import appeng.api.networking.energy.IAEPowerStorage
import appeng.api.networking.events.GridPowerStorageStateChanged
import com.gregtechceu.gtceu.api.GTValues
import com.gregtechceu.gtceu.api.capability.compat.FeCompat
import com.gregtechceu.gtceu.api.capability.recipe.IO
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder
import com.gregtechceu.gtceu.integration.ae2.utils.SerializableManagedGridNode
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder
import net.minecraft.core.Direction
import net.minecraft.server.TickTask
import net.minecraft.server.level.ServerLevel
import java.util.EnumSet
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
class AEPowerHatchMachine(
    holder: IMachineBlockEntity,
    tier: Int,
    amperage: Int,
) : TieredIOPartMachine(holder, tier, IO.OUT), IGridConnectedMachine, IAEPowerStorage {

    @Persisted
    private val energyContainer: AEPowerHatchEnergyContainer = AEPowerHatchEnergyContainer(this, tier, amperage)

    @Persisted
    private val nodeHolder: AEPowerHatchGridNodeTrait = AEPowerHatchGridNodeTrait(this)

    private var online = false

    /** 防抖：同一 server 任务只排一次队，避免能量频繁变化时刷屏。 */
    private var powerNotifyQueued = false

    /** 单 tick 可向 AE 输出的 EU 预算（= 额定输出 voltage × amperage），跨 tick 在 [extractAEPower] 内重置。 */
    private var aeOutputBudget = 0L

    /** 上次预算刷新的世界 tick；与当前 [net.minecraft.world.level.Level.gameTime] 不同则重置 [aeOutputBudget]。 */
    private var lastAeBudgetTick = Long.MIN_VALUE

    override fun getMainNode(): SerializableManagedGridNode = nodeHolder.mainNode

    override fun isOnline(): Boolean = online

    override fun setOnline(online: Boolean) {
        this.online = online
    }

    /**
     * 把本类声明的 `@Persisted` 字段（[energyContainer]、[nodeHolder]）纳入 SyncData 绑定链。
     * 基类 [TieredIOPartMachine] 的 MANAGED_FIELD_HOLDER 只覆盖到它自己的字段，若不在此处
     * 声明子类字段，[energyContainer.energyStored] 就不会被序列化——重载世界后仓内缓存归零。
     *
     * Extends the SyncData field chain so the fields declared in this class ([energyContainer],
     * [nodeHolder]) are bound too; without this the base holder only covers up to
     * [TieredIOPartMachine]'s own fields and [energyContainer.energyStored] is never persisted,
     * zeroing the buffer on world reload.
     */
    override fun getFieldHolder(): ManagedFieldHolder = MANAGED_FIELD_HOLDER

    companion object {
        private val MANAGED_FIELD_HOLDER = ManagedFieldHolder(
            AEPowerHatchMachine::class.java,
            TieredIOPartMachine.MANAGED_FIELD_HOLDER,
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

    internal fun queuePowerNotify() {
        if (powerNotifyQueued) return
        if (energyContainer.energyStored <= 0) return
        val lvl = level ?: return
        if (lvl !is ServerLevel) return
        powerNotifyQueued = true
        lvl.server.tell(TickTask(0) {
            powerNotifyQueued = false
            if (!isInValid() && energyContainer.energyStored > 0) {
                nodeHolder.mainNode.ifPresent { grid ->
                    grid.postEvent(
                        GridPowerStorageStateChanged(
                            this,
                            GridPowerStorageStateChanged.PowerEventType.PROVIDE_POWER,
                        ),
                    )
                }
            }
        })
    }

    override fun onRotated(oldFacing: Direction, newFacing: Direction) {
        super.onRotated(oldFacing, newFacing)
        nodeHolder.mainNode.setExposedOnSides(EnumSet.of(newFacing))
    }

    /**
     * Tier colour for the front-centre socket plate (overlay tint layer, tintindex 2), matching
     * GT's own energy/dynamo hatch tint rule (`EnergyHatchPartMachine.tintColor`).
     */
    override fun tintColor(index: Int): Int =
        if (index == 2) GTValues.VC[tier] else super.tintColor(index)

    // ----------------------------------------------------------------------------------
    //  AE 能源元件（IAEPowerStorage）
    // ----------------------------------------------------------------------------------

    /**
     * AE 网络按需抽取。把请求的 AE 量换算回 EU（向上取整到整 EU，保证网络对
     * `extractAEPower(0.1, SIMULATE, CONFIG)` 这类亚 EU 级探测也能给出非零结果），
     * 从 [energyContainer] 扣除并返回实际扣掉的 EU 对应的 AE 值（不做乘数除法，
     * 由 AE2 网络层统一处理）。成形与否不影响供电：只要仓内缓存非空即放行
     * （红石/工作禁用除外），这样世界重载后的首个网格 tick 就能抽到电，供电零断档。
     *
     * 抽取速率受 GT 额定输出限制：每个世界 tick 最多放行 `outputVoltage × outputAmperage`
     * （本仓作为 dynamo hatch 的额定输出）EU。否则多方块把缓冲填满后，AE 侧会在单 tick
     * 内把整仓缓存一次性抽空，等效吞吐变成「缓存大小 / tick」，远超 GT 原本的输出速度。
     * 缓冲只是储能，真正的「发电流量」仍是 GT 的额定输出。
     */
    override fun extractAEPower(amt: Double, mode: Actionable, usePowerMultiplier: PowerMultiplier): Double {
        val level = level ?: return 0.0
        if (level.isClientSide) return 0.0
        if (!isWorkingEnabled()) return 0.0
        if (amt <= 0.0) return 0.0

        val euToFeRatio = FeCompat.ratio(false)
        val requestedEu = aeToEuRoundedUp(amt, euToFeRatio)
        if (requestedEu <= 0) return 0.0

        val gameTime = level.gameTime
        if (gameTime != lastAeBudgetTick) {
            lastAeBudgetTick = gameTime
            aeOutputBudget = energyContainer.outputVoltage * energyContainer.outputAmperage
        }

        val extractEu = minOf(energyContainer.energyStored, requestedEu, aeOutputBudget)
        if (extractEu <= 0) return 0.0

        if (mode == Actionable.MODULATE) {
            energyContainer.setEnergyStored(energyContainer.energyStored - extractEu)
            aeOutputBudget -= extractEu
        }
        return euToAe(extractEu, euToFeRatio)
    }

    /** 只出不进：拒绝网络充回。 */
    override fun injectAEPower(amt: Double, mode: Actionable): Double = amt

    override fun getAEMaxPower(): Double = euToAe(energyContainer.energyCapacity)

    override fun getAECurrentPower(): Double = euToAe(energyContainer.energyStored)

    override fun isAEPublicPowerStorage(): Boolean = true

    override fun getPowerFlow(): AccessRestriction = AccessRestriction.READ

    private fun euToAe(eu: Long): Double = euToAe(eu, FeCompat.ratio(false))

    private fun euToAe(eu: Long, euToFeRatio: Int): Double =
        PowerUnits.FE.convertTo(PowerUnits.AE, FeCompat.toFeLong(eu, euToFeRatio).toDouble())

    /**
     * AE -> FE -> EU，向上取整：任何非零请求（含 0.1 AE 级探测）只要仓内还有 EU 就能得到
     * 至少 1 EU（对应当前换算比例下的整数 AE），避免亚 EU 请求被取整抹成 0。
     */
    private fun aeToEuRoundedUp(ae: Double, euToFeRatio: Int): Long {
        val fe = PowerUnits.AE.convertTo(PowerUnits.FE, ae)
        if (fe >= Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
        return ceil(fe / euToFeRatio).toLong()
    }
}

/**
 * 能量容器：纯缓冲。多方块产出的 EU 经 [handleRecipeInner] 注入，
 * 之后只被 AE 侧 [AEPowerHatchMachine.extractAEPower] 抽走，不再主动向任何方向输出 EU，
 * 因此 [serverTick] 覆写为空实现。
 *
 * Energy container: a pure buffer. EU produced by the multiblock flows in through
 * [handleRecipeInner] and is only drawn by the AE side via
 * [AEPowerHatchMachine.extractAEPower]; it no longer pushes EU anywhere, so [serverTick] is
 * overridden with an empty body to suppress the base GT energy output.
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

    override fun serverTick() {
        // EU only leaves the hatch through AE's IAEPowerStorage extraction.
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
class AEPowerHatchGridNodeTrait(
    connectedMachine: IGridConnectedMachine,
) : GridNodeHolder(connectedMachine) {

    override fun createManagedNode(): SerializableManagedGridNode {
        // setFlags/setIdlePowerUsage/addService return the base IManagedGridNode type, so the
        // cast is required to keep the SerializableManagedGridNode (matches GridNodeHolder).
        return super.createManagedNode()
            .setFlags()
            .setIdlePowerUsage(0.0)
            .addService(IAEPowerStorage::class.java, machine as AEPowerHatchMachine) as SerializableManagedGridNode
    }
}
