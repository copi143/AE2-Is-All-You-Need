package minecraftx.compose.demo

import allyouneed.Platform
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

/**
 * K/L 两个 Compose 演示屏的按键绑定,双加载器共用:注册走 [Platform.registerKeyBinding],
 * 每刻轮询走 [Platform.onClientTick](与 [minecraftx.compose.itemdetail.ItemDetailsKeyBind] 同一模式)。
 */
object DemoKeyBind {

    val openDemo = KeyMapping(
        "key.ae2isallyouneed.open_demo",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_K,
        "key.categories.misc",
    )
    val openEmbedded = KeyMapping(
        "key.ae2isallyouneed.open_embedded",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_L,
        "key.categories.misc",
    )

    private var registered = false

    fun init() {
        if (registered) return
        registered = true
        Platform.registerKeyBinding(openDemo)
        Platform.registerKeyBinding(openEmbedded)
        Platform.onClientTick(::tick)
    }

    private fun tick() {
        if (openDemo.consumeClick()) {
            // ComposeDemoScreen 是容器屏,需要玩家物品栏;不在世界中(主菜单)不打开。
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().setScreen(ComposeDemoScreen())
            }
        } else if (openEmbedded.consumeClick()) {
            Minecraft.getInstance().setScreen(EmbeddedComposeDemoScreen())
        }
    }
}
