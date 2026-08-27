package allyouneed.client.render

import allyouneed.logic.aekey.*
import appeng.api.client.AEKeyRenderHandler
import appeng.api.client.AEKeyRendering
import appeng.client.gui.style.Blitter
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.level.Level

/**
 * 为自定义 AEKeyType 注册客户端渲染器，否则会在终端 / 监视器渲染时抛出
 * `Missing render handler for channel ae2:xp` 等异常。
 *
 * 渲染复用对应 packet 的基础图标 (`item/xp_icon` 等)，与存储元件图标保持一致，
 * 通过 [Blitter] 在 GUI 与方块表面绘制，逻辑与 ExtendedAE 的 Flux 渲染一致。
 */
object AEKeyRenderers {

    fun init() {
        // LevelOnly: hp / sta / xp  -> 对应 hp_icon / sta_icon / xp_icon
        registerSafe(HpKey.Type, HpKey::class.java, LevelIconHandler(ResourceLocation("ae2isallyouneed", "item/hp_icon")))
        registerSafe(StaKey.Type, StaKey::class.java, LevelIconHandler(ResourceLocation("ae2isallyouneed", "item/sta_icon")))
        registerSafe(XpKey.Type, XpKey::class.java, LevelIconHandler(ResourceLocation("ae2isallyouneed", "item/xp_icon")))

        // MetricLevel: energy / mana  -> energy_icon / mana_icon（所有 metric 共用同一图标，tooltip 已区分 metric）
        registerSafe(EnergyKey.Type, EnergyKey::class.java, LevelIconHandler(ResourceLocation("ae2isallyouneed", "item/energy_icon")))
        registerSafe(ManaKey.Type, ManaKey::class.java, LevelIconHandler(ResourceLocation("ae2isallyouneed", "item/mana_icon")))

        // Virtual: 暂用 item_icon 作为通用图标
        registerSafe(VirtualKey.Type, VirtualKey::class.java, LevelIconHandler(ResourceLocation("ae2isallyouneed", "item/item_icon")))
    }

    private fun <T : appeng.api.stacks.AEKey> registerSafe(
        type: appeng.api.stacks.AEKeyType,
        clazz: Class<T>,
        handler: AEKeyRenderHandler<T>,
    ) {
        try {
            AEKeyRendering.register(type, clazz, handler)
        } catch (e: IllegalArgumentException) {
            // 重复注册（Fabric client+server 双路径 或 热重载）时忽略
            if (!e.message.orEmpty().contains("Duplicate registration")) throw e
        }
    }

    private class LevelIconHandler<T : appeng.api.stacks.AEKey>(private val icon: ResourceLocation) : AEKeyRenderHandler<T> {
        override fun drawInGui(minecraft: Minecraft, guiGraphics: GuiGraphics, x: Int, y: Int, stack: T) {
            val sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon)
            Blitter.sprite(sprite).blending(false).dest(x, y, 16, 16).blit(guiGraphics)
        }

        override fun drawOnBlockFace(
            poseStack: PoseStack,
            buffers: MultiBufferSource,
            what: T,
            scale: Float,
            combinedLight: Int,
            level: Level,
        ) {
            val sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon)
            val color = 0xFFFFFF
            poseStack.pushPose()
            poseStack.translate(0.0, 0.0, 0.01)
            val buffer = buffers.getBuffer(RenderType.solid())
            var s = scale - 0.05f
            val x0 = -s / 2
            val y0 = s / 2
            val x1 = s / 2
            val y1 = -s / 2
            val transform = poseStack.last().pose()
            buffer.vertex(transform, x0, y1, 0f).color(color).uv(sprite.u0, sprite.v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(combinedLight).normal(0f, 0f, 1f).endVertex()
            buffer.vertex(transform, x1, y1, 0f).color(color).uv(sprite.u1, sprite.v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(combinedLight).normal(0f, 0f, 1f).endVertex()
            buffer.vertex(transform, x1, y0, 0f).color(color).uv(sprite.u1, sprite.v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(combinedLight).normal(0f, 0f, 1f).endVertex()
            buffer.vertex(transform, x0, y0, 0f).color(color).uv(sprite.u0, sprite.v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(combinedLight).normal(0f, 0f, 1f).endVertex()
            poseStack.popPose()
        }

        override fun getDisplayName(stack: T): Component {
            return stack.displayName
        }
    }
}
