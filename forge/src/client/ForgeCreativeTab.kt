package allyouneed.client

import allyouneed.client.group.CreativeTabGroup
import allyouneed.client.group.CreativeTabGroupRegistry
import allyouneed.forge.init.ForgeBlocks
import allyouneed.forge.init.ForgeItems
import allyouneed.util.MODID
import allyouneed.util.rl
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraftforge.eventbus.api.IEventBus

object ForgeCreativeTab {
    val CREATIVE_TAB: CreativeModeTab = CreativeModeTab.builder().title(Component.translatable("itemGroup.$MODID"))
        .icon { ItemStack(ForgeItems.PSEUDO_PATTERN.get()) }.displayItems { _, output ->
            output.accept(ForgeItems.PSEUDO_PATTERN.get())
            output.accept(ForgeItems.PSEUDO_PATTERN.get())
            output.accept(ForgeItems.ENTITY_P2P_TUNNEL.get())
            output.accept(allyouneed.AllRegistries.CREATIVE_ME_CELL)
            output.accept(allyouneed.AllRegistries.DIMENSIONAL_CELL)
            output.accept(ForgeBlocks.WIRELESS_PSEUDO_PATTERN_TERMINAL.get())
            output.accept(ForgeItems.PATTERN_ENCODING_TERMINAL.get())
        }.build()

    val MOD_TAB_ID = "main".rl

    fun register(bus: IEventBus) {
        registerGroup()
    }

    private fun registerGroup() {
        CreativeTabGroupRegistry.register(
            CreativeTabGroup(
                "ae2_addon".rl,
                Component.translatable("itemGroup.$MODID"),
                { ItemStack(ForgeItems.PSEUDO_PATTERN.get()) }).addTab(MOD_TAB_ID)
        )
    }
}
