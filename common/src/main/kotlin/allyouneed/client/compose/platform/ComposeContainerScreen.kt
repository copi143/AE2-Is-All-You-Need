package allyouneed.client.compose.platform

import androidx.compose.runtime.Composable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

/**
 * 容器型 Compose 全屏屏:与 [ComposeScreen] 相同的 Compose 接线,但父类是
 * [AbstractContainerScreen]。EMI 等配方模组只把容器屏视为"当前处理屏"(handled screen),
 * 从这类屏内点击 ItemSlot 打开 EMI 配方时,EMI 会把本屏压入自己的返回栈,按 ESC 会回到本屏;
 * 而普通 [ComposeScreen] 会让 EMI 回落到 vanilla 物品栏。
 *
 * 子类只需实现 [Content];menu 统一传 [EmptyMenu](不含槽位,仅作 EMI 标记,配方填充会回落
 * 到玩家物品栏),玩家物品栏用 [playerInventory] 获取。K 演示屏与 V 详情屏都用本类,保证
 * EMI 返回逻辑一致。
 */
abstract class ComposeContainerScreen<T : AbstractContainerMenu>(
    menu: T,
    playerInventory: Inventory,
    title: Component,
) : AbstractContainerScreen<T>(menu, playerInventory, title) {

    protected val layer = ComposeLayer()

    @Composable
    abstract fun Content()

    override fun init() {
        super.init()
        // init() 在窗口缩放时也会执行;ComposeLayer.setContent 忽略重复调用。
        layer.setContent { Content() }
    }

    /** 当前整 UI 缩放系数;在 Composable 内读取会订阅变化。 */
    @Composable
    protected fun currentUiScale(): Float = layer.uiScale

    /** 供组合外部(如事件回调)读取的缩放系数。 */
    protected fun uiScaleFactor(): Float = layer.uiScale

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        // 强制按新窗口尺寸重新测量,布局跟随 GUI 缩放/窗口变化(根约束每帧也刷新)。
        layer.onScreenResize()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        layer.render(graphics, mouseX, mouseY, partialTick, layer.fullScreenRect(width, height))
    }

    // 内容完全由 Compose 渲染,不绘制 vanilla 容器背景。
    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) = Unit

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (layer.onMouseClicked(mouseX, mouseY, button)) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        if (hasControlDown()) {
            // Ctrl+滚轮缩放整个 Compose UI(0.5x..4x)。
            layer.setUiScaleFactor(layer.uiScale + (delta * UI_SCALE_STEP).toFloat())
            return true
        }
        if (layer.onMouseScrolled(mouseX, mouseY, delta)) return true
        return super.mouseScrolled(mouseX, mouseY, delta)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (layer.onMouseReleased(mouseX, mouseY, button)) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun onClose() {
        layer.dispose()
        // 等价于 Screen.onClose(setScreen(null)),但跳过 AbstractContainerScreen 的
        // closeContainer:本类菜单是空 dummy,关闭它不应连带关闭玩家当前真实打开的容器菜单
        // (例如 V 详情屏是从容器屏打开的,关闭详情后要原样回到那个容器屏)。
        Minecraft.getInstance().setScreen(null)
    }

    // AbstractContainerScreen 默认不暂停游戏,但 ComposeScreen 沿用 Screen 的默认暂停行为,这里保持一致。
    override fun isPauseScreen() = true

    /**
     * 空的容器菜单:没有槽位,仅用于让 EMI 把 Compose 屏识别为容器屏(handled screen),
     * 以便从屏内打开 EMI 配方后按 ESC 能回到本屏;配方填充会回落到玩家物品栏。
     */
    class EmptyMenu : AbstractContainerMenu(null, 0) {
        override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY
        override fun stillValid(player: Player): Boolean = true
    }

    companion object {
        /** 当前玩家物品栏,用于构造容器屏;不在世界中时退化为空物品栏。 */
        fun playerInventory(): Inventory = Minecraft.getInstance().player?.inventory ?: Inventory(null)

        private const val UI_SCALE_STEP = 0.1f
    }
}
