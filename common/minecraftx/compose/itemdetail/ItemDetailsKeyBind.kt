package minecraftx.compose.itemdetail

import allyouneed.Platform
import minecraftx.compose.itemdetail.focus.ItemDetailsFocus
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import org.lwjgl.glfw.GLFW

/**
 * Registers the V key binding that opens the item-details screen for the item
 * currently under the cursor. Called once from both loaders' key registration.
 *
 * Vanilla only forwards key presses to [KeyMapping]s while no screen is open, so
 * inside container/inventory screens (where the hovered-item use case lives) we
 * also edge-detect the raw GLFW key state ourselves.
 */
object ItemDetailsKeyBind {
    const val KEY_ID = "key.ae2isallyouneed.item_details"
    const val CATEGORY_ID = "key.categories.ae2isallyouneed"

    lateinit var key: KeyMapping
        private set

    private var registered = false
    private var wasKeyDown = false

    fun init() {
        if (registered) return
        registered = true

        key = KeyMapping(KEY_ID, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY_ID)
        Platform.registerKeyBinding(key)
        Platform.onClientTick(::tick)
    }

    private fun tick() {
        if (key.consumeClick()) {
            wasKeyDown = InputConstants.isKeyDown(Minecraft.getInstance().window.window, GLFW.GLFW_KEY_V)
            openForHovered()
            return
        }
        val rawDown = InputConstants.isKeyDown(Minecraft.getInstance().window.window, GLFW.GLFW_KEY_V)
        if (rawDown && !wasKeyDown && !hasTextInputFocus()) {
            openForHovered()
        }
        wasKeyDown = rawDown
    }

    private fun hasTextInputFocus(): Boolean {
        val screen = Minecraft.getInstance().screen ?: return false
        return screen.focused is EditBox
    }

    private fun openForHovered() {
        val stack = ItemDetailsFocus.hoveredStack()
        if (stack != null && !stack.isEmpty) {
            ItemDetailsScreen.prepareReturn(Minecraft.getInstance().screen)
            Minecraft.getInstance().setScreen(ItemDetailsScreenFactory.create(stack))
        }
    }
}
