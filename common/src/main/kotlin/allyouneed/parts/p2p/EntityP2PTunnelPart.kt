package allyouneed.parts.p2p

import appeng.api.config.PowerUnits
import appeng.api.parts.IPartItem
import appeng.api.parts.IPartModel
import appeng.items.parts.PartModels
import appeng.parts.p2p.P2PModels
import appeng.parts.p2p.P2PTunnelPart
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity

class EntityP2PTunnelPart(partItem: IPartItem<*>) : P2PTunnelPart<EntityP2PTunnelPart>(partItem) {

    companion object {
        private val MODELS = P2PModels(
            ResourceLocation("ae2isallyouneed", "part/p2p/p2p_tunnel_entity")
        )

        @JvmStatic
        @PartModels
        fun getModels(): List<IPartModel> = MODELS.getModels()
    }

    private var nextOutputIndex: Int = 0

    override fun getPowerDrainPerTick(): Float = 2.0f

    override fun getStaticModels(): IPartModel = MODELS.getModel(this.isPowered(), this.isActive())

    override fun onTunnelNetworkChange() {
    }

    override fun onEntityCollision(entity: Entity) {
        if (isOutput()) return
        if (!mainNode.isActive) return
        if (entity.portalCooldown > 0) return

        val hostBe = blockEntity ?: return
        val level = hostBe.level ?: return
        if (level.isClientSide) return

        val outputs = getOutputs()
        if (outputs.isEmpty()) return

        val index = nextOutputIndex % outputs.size
        nextOutputIndex = (nextOutputIndex + 1) % outputs.size
        host.markForSave()

        val target = outputs[index]
        val targetBe = target.blockEntity ?: return
        val targetLevel = targetBe.level ?: return

        if (targetLevel !== level) return

        val targetPos = targetBe.blockPos.relative(target.side)
        val x = targetPos.x + 0.5
        val y = targetPos.y + 0.1
        val z = targetPos.z + 0.5

        entity.teleportTo(x, y, z)
        entity.portalCooldown = 20
        queueTunnelDrain(PowerUnits.AE, 1.0)
    }

    override fun readFromNBT(data: CompoundTag) {
        super.readFromNBT(data)
        nextOutputIndex = data.getInt("nextOutputIndex")
    }

    override fun writeToNBT(data: CompoundTag) {
        super.writeToNBT(data)
        data.putInt("nextOutputIndex", nextOutputIndex)
    }
}
