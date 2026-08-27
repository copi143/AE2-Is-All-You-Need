package allyouneed.parts.automation

import allyouneed.logic.aekey.XpKey
import appeng.api.behaviors.PickupSink
import appeng.api.behaviors.PickupStrategy
import appeng.api.config.Actionable
import appeng.api.networking.energy.IEnergySource
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.UUID

/**
 * 破坏面板经验球吸收策略。
 *
 * - 普通经验固定为 [XpKey] `level=1`
 * - 复用默认存储成本（amountPerByte=8），由 [PickupSink.insert] 经由
 *   [appeng.api.storage.StorageHelper.poweredInsert] 自动扣电
 * - 仅处理实体碰撞路径；方块破坏产生的经验由 [allyouneed.mixin.ae2.ItemPickupStrategyMixin] 在
 *   [appeng.parts.automation.ItemPickupStrategy.completePickup] 尾部追加处理
 */
class XpPickupStrategy(
    private val level: ServerLevel,
    private val pos: BlockPos,
    private val side: Direction,
    @Suppress("unused") private val host: BlockEntity?,
    @Suppress("unused") private val enchantments: Map<Enchantment, Int>?,
    @Suppress("unused") private val owningPlayerId: UUID?,
) : PickupStrategy {

    companion object {
        const val XP_NORMAL_LEVEL = 1
    }

    private var isAccepting = true

    override fun reset() {
        isAccepting = true
    }

    override fun canPickUpEntity(entity: Entity): Boolean {
        return entity is ExperienceOrb
    }

    override fun pickUpEntity(energySource: IEnergySource, sink: PickupSink, entity: Entity): Boolean {
        if (!isAccepting) return false
        if (entity !is ExperienceOrb) return false
        if (!entity.isAlive) return false

        val orbValue = entity.value
        if (orbValue <= 0) {
            entity.discard()
            return true
        }

        val key = XpKey(XP_NORMAL_LEVEL)
        // 先模拟检查是否能存入，避免吞球
        val simulated = sink.insert(key, orbValue.toLong(), Actionable.SIMULATE)
        if (simulated <= 0L) {
            isAccepting = false
            return false
        }

        val inserted = sink.insert(key, orbValue.toLong(), Actionable.MODULATE)
        if (inserted <= 0L) {
            isAccepting = false
            return false
        }

        isAccepting = inserted >= orbValue

        if (inserted >= orbValue) {
            entity.discard()
        } else {
            // 部分存入：用剩余值重建一个经验球，避免丢失
            val remaining = orbValue - inserted.toInt()
            entity.discard()
            if (remaining > 0) {
                // 使用原位置生成剩余经验球，下一 tick 仍会被面板捕获或由玩家拾取
                val remainingOrb = ExperienceOrb(level, entity.x, entity.y, entity.z, remaining)
                level.addFreshEntity(remainingOrb)
            }
        }

        // 可选：发送与物品相同的过渡特效，由 AE2 控制；经验球无需特效，静默处理

        return true
    }

    override fun tryPickup(energySource: IEnergySource, sink: PickupSink): PickupStrategy.Result {
        // 方块破坏经验不在此策略处理，由 ItemPickupStrategyMixin 在方块被破坏后追加入网
        return PickupStrategy.Result.CANT_PICKUP
    }
}
