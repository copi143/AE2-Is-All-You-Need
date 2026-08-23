@file:Suppress("UnstableApiUsage")

package allyouneed.parts.planebus

import allyouneed.util.MODID
import allyouneed.util.rl
import appeng.api.integrations.igtooltip.ClientRegistration
import appeng.api.integrations.igtooltip.CommonRegistration
import appeng.api.integrations.igtooltip.TooltipBuilder
import appeng.api.integrations.igtooltip.TooltipContext
import appeng.api.integrations.igtooltip.TooltipProvider
import appeng.api.integrations.igtooltip.PartTooltips
import appeng.api.integrations.igtooltip.providers.BodyProvider
import appeng.api.integrations.igtooltip.providers.ServerDataProvider
import appeng.api.parts.IPart
import appeng.parts.AEBasePart
import appeng.parts.automation.AnnihilationPlanePart
import appeng.parts.automation.FormationPlanePart
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player

/**
 * Jade / WTHIT / TOP 集成（经 AE2 的 igtooltip SPI）：悬停专用线缆或入簇面板时，
 * 显示已连接的破坏面板与成型面板数量、互联的专用线缆数量及结构成型状态。
 *
 * Jade / WTHIT / TOP integration via AE2's igtooltip SPI: hovering the plane bus or a
 * clustered plane shows how many annihilation/forming planes are connected, how many buses
 * are interconnected and whether the structure is formed.
 *
 * 服务端把集群统计写进同步数据，客户端据此渲染；未入簇的面板不产生任何行。
 * The server writes cluster stats into the synced data; the client renders lines from it.
 * Planes outside any cluster produce no lines.
 */
class PlaneBusTooltipProvider : TooltipProvider {

    override fun registerCommon(registration: CommonRegistration) {
        PartTooltips.addServerData(
            IPart::class.java,
            PlaneBusDataProvider,
            TooltipProvider.DEFAULT_PRIORITY,
        )
    }

    override fun registerClient(registration: ClientRegistration) {
        PartTooltips.addBody(
            IPart::class.java,
            PlaneBusDataProvider,
            TooltipProvider.DEFAULT_PRIORITY,
        )
    }

    private object PlaneBusDataProvider : BodyProvider<IPart>, ServerDataProvider<IPart> {

        @JvmField
        val ID: ResourceLocation = "plane_bus".rl

        private const val KEY_FORMED = "formed"
        private const val KEY_ANNIHILATIONS = "annihilations"
        private const val KEY_FORMINGS = "formings"
        private const val KEY_BUSES = "buses"

        override fun provideServerData(player: Player, part: IPart, serverData: CompoundTag) {
            if (!isRelevantPart(part)) return
            val be = (part as? AEBasePart)?.blockEntity ?: return
            val level = be.level ?: return
            val info = PlaneBusClusters.snapshotFor(level.dimension()).infoAt(be.blockPos) ?: return
            serverData.putBoolean(KEY_FORMED, info.formed)
            serverData.putInt(KEY_ANNIHILATIONS, info.annihilations)
            serverData.putInt(KEY_FORMINGS, info.formings)
            serverData.putInt(KEY_BUSES, info.buses)
        }

        override fun buildTooltip(part: IPart, context: TooltipContext, tooltip: TooltipBuilder) {
            val data = context.serverData()
            if (!data.contains(KEY_FORMED)) return
            tooltip.addLine(
                Component.translatable(
                    "tooltip.$MODID.plane_bus.members",
                    data.getInt(KEY_ANNIHILATIONS),
                    data.getInt(KEY_FORMINGS),
                ),
                ID,
            )
            tooltip.addLine(
                Component.translatable("tooltip.$MODID.plane_bus.buses", data.getInt(KEY_BUSES)),
                ID,
            )
            if (!data.getBoolean(KEY_FORMED)) {
                tooltip.addLine(Component.translatable("tooltip.$MODID.plane_bus.unformed"), ID)
            }
        }

        /** 线缆本体，或可能入簇的两类面板。The bus itself, or the two plane types. */
        private fun isRelevantPart(part: IPart): Boolean =
            part is PlaneBusPart ||
                part is AnnihilationPlanePart ||
                part is FormationPlanePart
    }
}
