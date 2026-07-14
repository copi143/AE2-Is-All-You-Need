package allyouneed.client

import allyouneed.Constants
import allyouneed.client.group.CreativeTabGroup
import allyouneed.client.group.CreativeTabGroupRegistry
import allyouneed.forge.init.ForgeBlocks
import allyouneed.forge.init.ForgeItems
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraftforge.eventbus.api.IEventBus

object ForgeCreativeTab {
    val CREATIVE_TAB: CreativeModeTab = CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.${Constants.MOD_ID}"))
        .icon { ItemStack(ForgeItems.MACHINE_PATTERN.get()) }
        .displayItems { _, output ->
            output.accept(ForgeItems.MACHINE_PATTERN.get())
            output.accept(ForgeItems.PSEUDO_PATTERN.get())
            output.accept(ForgeItems.ENTITY_P2P_TUNNEL.get())
            output.accept(ForgeBlocks.WIRELESS_PSEUDO_PATTERN_TERMINAL.get())
            output.accept(ForgeBlocks.PSEUDO_PATTERN_TERMINAL.get().asItem())
        }
        .build()

    val MOD_TAB_ID = ResourceLocation(Constants.MOD_ID, "main")

    fun register(bus: IEventBus) {
        registerGroup()
    }

    private fun registerGroup() {
        CreativeTabGroupRegistry.register(
            CreativeTabGroup(
                ResourceLocation(Constants.MOD_ID, "ae2_addon"),
                Component.translatable("itemGroup.${Constants.MOD_ID}"),
                { ItemStack(ForgeItems.MACHINE_PATTERN.get()) }
            ).addTab(MOD_TAB_ID)
        )
    }
}
