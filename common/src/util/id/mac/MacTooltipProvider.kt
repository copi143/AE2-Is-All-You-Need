@file:Suppress("UnstableApiUsage")

package allyouneed.util.id.mac

import allyouneed.util.MODID
import appeng.api.integrations.igtooltip.*
import appeng.api.integrations.igtooltip.providers.BodyProvider
import appeng.api.integrations.igtooltip.providers.ServerDataProvider
import appeng.api.parts.IPart
import appeng.block.AEBaseEntityBlock
import appeng.blockentity.AEBaseBlockEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * Jade / WTHIT / TOP integration via AE2's igtooltip SPI.
 * Shows each grid node's 48-bit MAC on networked block entities and parts.
 */
class MacTooltipProvider : TooltipProvider {

    override fun registerCommon(registration: CommonRegistration) {
        registration.addBlockEntityData(AEBaseBlockEntity::class.java, BeMacProvider)
        PartTooltips.addServerData(IPart::class.java, PartMacProvider, TooltipProvider.DEFAULT_PRIORITY + 50)
    }

    override fun registerClient(registration: ClientRegistration) {
        registration.addBlockEntityBody(
            AEBaseBlockEntity::class.java,
            AEBaseEntityBlock::class.java,
            ID,
            BeMacProvider,
            TooltipProvider.DEFAULT_PRIORITY + 50,
        )
        PartTooltips.addBody(IPart::class.java, PartMacProvider, TooltipProvider.DEFAULT_PRIORITY + 50)
    }

    private object BeMacProvider : BodyProvider<BlockEntity>, ServerDataProvider<BlockEntity> {
        override fun provideServerData(player: Player, obj: BlockEntity, serverData: CompoundTag) {
            MacTooltipTexts.writeServerData(serverData, MacHosts.collectMacs(obj))
        }

        override fun buildTooltip(obj: BlockEntity, context: TooltipContext, tooltip: TooltipBuilder) {
            for (line in MacTooltipTexts.linesFromMacs(MacTooltipTexts.readServerData(context.serverData()))) {
                tooltip.addLine(line, ID)
            }
        }
    }

    private object PartMacProvider : BodyProvider<IPart>, ServerDataProvider<IPart> {
        override fun provideServerData(player: Player, part: IPart, serverData: CompoundTag) {
            MacTooltipTexts.writeServerData(serverData, MacHosts.collectMacs(part))
        }

        override fun buildTooltip(part: IPart, context: TooltipContext, tooltip: TooltipBuilder) {
            for (line in MacTooltipTexts.linesFromMacs(MacTooltipTexts.readServerData(context.serverData()))) {
                tooltip.addLine(line, ID)
            }
        }
    }

    companion object {
        @JvmField
        val ID: ResourceLocation = ResourceLocation(MODID, "mac")
    }
}
