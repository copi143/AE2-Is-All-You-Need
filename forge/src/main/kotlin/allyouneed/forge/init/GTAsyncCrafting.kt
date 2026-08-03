package allyouneed.forge.init

import allyouneed.async.AsyncBlockKind
import allyouneed.async.AsyncBlockRegistry
import allyouneed.gt.AsyncStructureGtConnectorMachine
import allyouneed.gt.AsyncStructureGtControllerMachine
import allyouneed.gt.AsyncStructureGtFactoryMachine
import allyouneed.gt.AsyncStructureGtLanConnectorMachine
import allyouneed.gt.AsyncStructureGtMachineBlock
import allyouneed.gt.AsyncStructureGtMeConnectorMachine
import allyouneed.gt.AsyncStructureGtPattern
import allyouneed.gt.AsyncStructureGtProcessorMachine
import allyouneed.gt.AsyncStructureGtSwitchMachine
import allyouneed.gt.AsyncStructureGtWanConnectorMachine
import allyouneed.multiblock.AsyncStructureType
import allyouneed.rl
import allyouneed.util.MODID
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import com.gregtechceu.gtceu.api.GTCEuAPI
import com.gregtechceu.gtceu.api.block.IMachineBlock
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity
import com.gregtechceu.gtceu.api.item.MetaMachineItem
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity
import com.gregtechceu.gtceu.api.machine.MachineDefinition
import com.gregtechceu.gtceu.api.machine.MetaMachine
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate
import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.registries.RegisterEvent
import org.apache.commons.lang3.function.TriFunction
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

/**
 * GTCEu registration glue for the async synthesis controllers and connectors. Everything GT-specific
 * (the machine classes, the placeholder pattern and the status menu) lives in common; this object
 * only wires it into GTCEu's registrate and the AE2 creative tab.
 *
 * Kotlin-for-Forge caveat: [GTRegistrate.registerEventListeners] reaches into
 * `FMLJavaModLoadingContext.get()`, which is not the active loading context under KFF and throws a
 * ClassCastException. The same listeners are therefore registered on the KFF MOD_BUS directly via a
 * small subclass that exposes the protected registrate handlers. That subclass also overrides
 * `getModEventBus()` so the registrate's internal registration steps (e.g. render layer setup via
 * `OneTimeEventReceiver`) use the KFF MOD_BUS instead of the broken loading context lookup.
 *
 * Construction caveat: GTCEu registers materials, machines and recipe types from its own
 * `FMLConstructModEvent.enqueueWork` task, which runs after every mod constructor and freezes its
 * registries as it goes. Touching any GT static state from our constructor therefore races GTCEu's
 * content setup. The machines are instead registered from GTCEu's `GTCEuAPI.RegisterEvent` for the
 * machine registry, which GTCEu fires once its own machines are registered but before the machine
 * registry is frozen.
 */
object GTAsyncCrafting {
    lateinit var registrate: GTRegistrate
        private set

    private val controllerKinds: List<Triple<AsyncBlockKind, AsyncStructureType, (IMachineBlockEntity) -> AsyncStructureGtControllerMachine>> =
        listOf(
            Triple(AsyncBlockKind.CONTROLLER, AsyncStructureType.PROCESSOR) { holder ->
                AsyncStructureGtProcessorMachine(holder)
            },
            Triple(AsyncBlockKind.SWITCH, AsyncStructureType.SWITCH) { holder ->
                AsyncStructureGtSwitchMachine(holder)
            },
            Triple(AsyncBlockKind.FACTORY, AsyncStructureType.MODULE) { holder ->
                AsyncStructureGtFactoryMachine(holder)
            },
        )

    private val connectorKinds: List<Pair<AsyncBlockKind, (IMachineBlockEntity) -> AsyncStructureGtConnectorMachine>> =
        listOf(
            AsyncBlockKind.ME_CONNECTOR to { holder -> AsyncStructureGtMeConnectorMachine(holder) },
            AsyncBlockKind.WAN_CONNECTOR to { holder -> AsyncStructureGtWanConnectorMachine(holder) },
            AsyncBlockKind.LAN_CONNECTOR to { holder -> AsyncStructureGtLanConnectorMachine(holder) },
        )

    /** Controller / connector kind -> its GT machine definition. */
    private val definitions = HashMap<AsyncBlockKind, MachineDefinition>()

    fun definition(kind: AsyncBlockKind): MachineDefinition? = definitions[kind]

    fun isGtOwned(kind: AsyncBlockKind): Boolean = kind in definitions

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

        // GTCEu posts this mod-bus event after registering its own machines and before freezing the
        // machine registry, which is the only safe window to add addon machines.
        bus.addGenericListener(
            MachineDefinition::class.java,
            Consumer<GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition>> { registerMachines() },
        )

        // The GT block/item RegistryObjects are only populated once the block/item registry events
        // have run, so the AsyncBlockRegistry hand-off and the AE2 creative tab hand-off are both
        // deferred until common setup.
        bus.addListener(Consumer<FMLCommonSetupEvent> {
            for ((kind, definition) in definitions) {
                val block: Block = definition.getBlock()
                val item = definition.getItem()
                AsyncBlockRegistry.register(kind, block)
                BlockDefinition(kind.displayName, kind.id.rl, block, item).also {
                    MainCreativeTab.add(it)
                }
            }
        })
    }

    /** Registers the three controllers and the three connectors with GTRegistrate. */
    private fun registerMachines() {
        val registrate = this.registrate
        for ((kind, type, machineFactory) in controllerKinds) {
            val definition = registrate.multiblock(
                kind.id,
                machineFactory,
                blockFactory<MultiblockMachineDefinition>(kind),
                itemFactory(),
                blockEntityFactory(),
            )
                .pattern { AsyncStructureGtPattern.build(type, it) }
                .allowFlip(false)
                .model(asyncStructureModel(kind))
                .register()
            definitions[kind] = definition
        }
        for ((kind, machineFactory) in connectorKinds) {
            val definition = registrate.machine(
                kind.id,
                Function<ResourceLocation, MachineDefinition> { MachineDefinition(it) },
                machineFactory,
                blockFactory<MachineDefinition>(kind),
                itemFactory(),
                blockEntityFactory(),
            )
                .modelProperty(GTMachineModelProperties.IS_FORMED, false)
                .model(asyncStructureModel(kind))
                .register()
            definitions[kind] = definition
        }
    }

    /**
     * GT machine model initializer: delegates every GT machine render state to the static async
     * cube_all models generated by common/resgen (`block/async/<id>.json`), flipping to the
     * `_formed` variant once the GT `is_formed` render state is set. GTCEu's datagen otherwise
     * defaults to `block/machine/template/<id>`, a model file this addon never ships, which is why
     * the GT-owned blocks rendered as missing models.
     */
    private fun asyncStructureModel(kind: AsyncBlockKind): MachineBuilder.ModelInitializer =
        MachineBuilder.ModelInitializer { _, prov, builder ->
            val unformed = prov.models().getExistingFile("block/async/${kind.id}".rl(MODID))
            val formed = prov.models().getExistingFile("block/async/${kind.id}_formed".rl(MODID))
            builder.forAllStatesModels { state ->
                val formedState = state.hasProperty(GTMachineModelProperties.IS_FORMED) &&
                    state.getValue(GTMachineModelProperties.IS_FORMED)
                if (formedState) formed else unformed
            }
        }

    private fun <D : MachineDefinition> blockFactory(kind: AsyncBlockKind): BiFunction<BlockBehaviour.Properties, D, IMachineBlock> =
        BiFunction<BlockBehaviour.Properties, D, IMachineBlock> { props, definition ->
            AsyncStructureGtMachineBlock(props, definition, kind)
        }

    private fun itemFactory(): BiFunction<IMachineBlock, Item.Properties, MetaMachineItem> =
        BiFunction<IMachineBlock, Item.Properties, MetaMachineItem> { block, props -> MetaMachineItem(block, props) }

    private fun blockEntityFactory(): TriFunction<BlockEntityType<*>, BlockPos, BlockState, IMachineBlockEntity> =
        TriFunction<BlockEntityType<*>, BlockPos, BlockState, IMachineBlockEntity> { type, pos, state ->
            MetaMachineBlockEntity(type, pos, state)
        }
}
