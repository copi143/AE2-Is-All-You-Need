package allyouneed.forge.init

import allyouneed.gtceu.AEPowerHatchMachine
import allyouneed.util.MODID
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import com.gregtechceu.gtceu.api.GTCEuAPI
import com.gregtechceu.gtceu.api.GTValues
import com.gregtechceu.gtceu.api.data.RotationState
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity
import com.gregtechceu.gtceu.api.machine.MachineDefinition
import com.gregtechceu.gtceu.api.machine.MetaMachine
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine
import com.gregtechceu.gtceu.utils.FormattingUtil
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.registries.RegisterEvent
import java.util.function.Consumer
import java.util.function.Function

/**
 * AE 动力仓的 GTCEu 注册胶水，结构与 [GTAsyncCrafting] 相同：在 GTCEu 机器注册表的
 * `RegisterEvent` 窗口内，逐档调用 `registrate.machine`（用默认 [com.gregtechceu.gtceu.api.block.MetaMachineBlock]
 * 方块，静态方块状态只含 `facing`），并把成品放进 AE2 创造模式标签页。
 *
 * GTCEu registration glue for the AE power hatch, structured like [GTAsyncCrafting]: within
 * GTCEu's machine-registry `RegisterEvent` window it calls `registrate.machine` per tier (with
 * the default [com.gregtechceu.gtceu.api.block.MetaMachineBlock], so the static blockstate only
 * carries `facing`) and hands the resulting definitions to the AE2 creative tab.
 */
object GTAEPowerHatch {
    const val BASE_ID = "ae_power_hatch"

    /** Amperage variants, GT style: 2A is the base variant (no suffix); per GT's rule it reuses the
     *  1A overlay set, while 4A/16A/64A carry their own suffix and amperage overlay sets. */
    private val AMPERAGES = listOf(2, 4, 16, 64)

    lateinit var registrate: GTRegistrate
        private set

    private val definitions = HashMap<Pair<Int, Int>, MachineDefinition>()

    fun definition(tier: Int, amperage: Int): MachineDefinition? = definitions[tier to amperage]

    fun init(bus: IEventBus) {
        val registrate = object : GTRegistrate(MODID) {
            override fun getModEventBus(): IEventBus = bus

            fun registerListeners() {
                bus.addListener(EventPriority.LOW, Consumer<RegisterEvent> { onRegister(it) })
                bus.addListener(EventPriority.LOWEST, Consumer<RegisterEvent> { onRegisterLate(it) })
                bus.addListener(Consumer<BuildCreativeModeTabContentsEvent> { onBuildCreativeModeTabContents(it) })
            }
        }
        this.registrate = registrate
        registrate.registerListeners()

        // Same registration window as GTAsyncCrafting: after GTCEu's own machines, before the
        // machine registry freezes.
        bus.addGenericListener(
            MachineDefinition::class.java,
            Consumer<GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition>> { registerMachines() },
        )

        // The GT block/item RegistryObjects are populated by the time common setup runs.
        bus.addListener(Consumer<FMLCommonSetupEvent> {
            for ((key, definition) in definitions) {
                val display = if (key.second == 2) {
                    "${GTValues.VNF[key.first]} AE Power Hatch"
                } else {
                    "${GTValues.VNF[key.first]} ${key.second}A AE Power Hatch"
                }
                BlockDefinition(
                    display,
                    definition.id,
                    definition.block,
                    definition.item,
                ).also { MainCreativeTab.add(it) }
            }
        })
    }

    /** Registers the power hatches for every amperage variant; the blockstate carries only `facing`.
     *  2A spans all tiers (ULV..MAX); 4A/16A also span all tiers; 64A only exists from EV up. */
    private fun registerMachines() {
        val registrate = this.registrate
        for (amp in AMPERAGES) {
            val startTier = if (amp == 64) GTValues.EV else GTValues.ULV
            for (tier in GTValues.tiersBetween(startTier, GTValues.MAX)) {
                val id = if (amp == 2) {
                    "${GTValues.VN[tier].lowercase()}_$BASE_ID"
                } else {
                    "${GTValues.VN[tier].lowercase()}_${BASE_ID}_${amp}a"
                }
                val definition = registrate.machine(
                    id,
                    Function<IMachineBlockEntity, MetaMachine> { holder -> AEPowerHatchMachine(holder, tier, amp) },
                )
                    .tier(tier)
                    .rotationState(RotationState.NON_Y_AXIS)
                    .abilities(PartAbility.OUTPUT_ENERGY)
                    .tooltips(
                        Component.translatable(
                            "gtceu.universal.tooltip.voltage_out",
                            FormattingUtil.formatNumbers(GTValues.V[tier]),
                            GTValues.VNF[tier],
                        ),
                        Component.translatable("gtceu.universal.tooltip.amperage_out", amp),
                        Component.translatable(
                            "gtceu.universal.tooltip.energy_storage_capacity",
                            FormattingUtil.formatNumbers(EnergyHatchPartMachine.getHatchEnergyCapacity(tier, amp)),
                        ),
                        Component.translatable("block.$MODID.$BASE_ID.tooltip"),
                    )
                    // AE power hatch look: overlayTieredHullModel wraps the per-amperage part model
                    // (which mirrors GT's energy_output_hatch layout) and injects the per-tier hull at
                    // runtime. Only the front-centre overlay differs from GT's dynamo hatch (the arrow
                    // is re-themed to AE cable purple); the ring and tinted plate are GT's own assets.
                    .overlayTieredHullModel("ae_power_hatch")
                    .register()
                definitions[tier to amp] = definition
            }
        }
    }
}
