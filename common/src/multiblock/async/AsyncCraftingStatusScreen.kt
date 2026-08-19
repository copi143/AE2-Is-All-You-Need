package allyouneed.multiblock.async

import appeng.client.gui.AEBaseScreen
import appeng.client.gui.style.ScreenStyle
import appeng.menu.AEBaseMenu
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * async 合成处理器的状态界面，对所有宿主通用（自有方块与 GTCEu 机器共用）。
 * 除已成形/已接入网格之外，还展示通道吞噬状态、总存储容量与方块数；只有成形、
 * 已接入、且（无限通道模式下或）吞满了 32 条通道，才判定为“工作中”。
 *
 * Status screen of the async synthesis processor, shared by every host flavour (own block and
 * GTCEu machines). Besides formed/grid-connected it shows the swallowed-channels state, total
 * storage capacity and block count; it is considered "working" only when formed, connected, and
 * (in infinite channel mode or) swallowing the full 32 channels.
 */
class AsyncCraftingStatusScreen<M>(
    menu: M,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle,
) : AEBaseScreen<M>(menu, playerInventory, title, style) where M : AEBaseMenu, M : IAsyncCraftingStatusView {

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
