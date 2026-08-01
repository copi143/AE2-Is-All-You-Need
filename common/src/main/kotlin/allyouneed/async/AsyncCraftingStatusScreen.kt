package allyouneed.async

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import appeng.client.gui.AEBaseScreen
import appeng.client.gui.style.ScreenStyle

class AsyncCraftingStatusScreen(
    menu: AsyncCraftingStatusMenu,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle,
) : AEBaseScreen<AsyncCraftingStatusMenu>(menu, playerInventory, title, style) {

    override fun updateBeforeRender() {
        super.updateBeforeRender()

        val formed = menu.formed == 1
        val connected = menu.gridConnected == 1
        val infinite = menu.infiniteChannelMode == 1
        val working = formed && connected && (infinite || menu.swallowedChannels == MAX_SWALLOWED_CHANNELS)

        setTextContent(
            "status_title",
            Component.translatable("gui.ae2isallyouneed.async.status.title"),
        )
        setTextContent(
            "formed",
            Component.translatable(
                if (formed) "gui.ae2isallyouneed.async.status.formed"
                else "gui.ae2isallyouneed.async.status.unformed",
            ).withStyle(if (formed) ChatFormatting.GREEN else ChatFormatting.RED),
        )
        setTextContent(
            "grid_connected",
            Component.translatable(
                if (connected) "gui.ae2isallyouneed.async.status.connected"
                else "gui.ae2isallyouneed.async.status.disconnected",
            ).withStyle(if (connected) ChatFormatting.GREEN else ChatFormatting.DARK_RED),
        )
        setTextContent(
            "swallowed",
            Component.translatable(
                if (infinite) "gui.ae2isallyouneed.async.status.swallowed_infinite"
                else "gui.ae2isallyouneed.async.status.swallowed",
                menu.swallowedChannels,
            ),
        )
        setTextContent(
            "storage",
            Component.translatable(
                "gui.ae2isallyouneed.async.status.storage",
                menu.storageBytes / (1024L * 1024L),
            ),
        )
        setTextContent(
            "block_count",
            Component.translatable("gui.ae2isallyouneed.async.status.block_count", menu.blockCount),
        )
        setTextContent(
            "state",
            Component.translatable(
                if (working) "gui.ae2isallyouneed.async.status.working"
                else "gui.ae2isallyouneed.async.status.not_working",
            ).withStyle(if (working) ChatFormatting.GREEN else ChatFormatting.RED),
        )
    }

    companion object {
        const val MAX_SWALLOWED_CHANNELS = 32
    }
}
