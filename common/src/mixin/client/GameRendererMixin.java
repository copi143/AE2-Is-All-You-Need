package allyouneed.mixin.client;

import allyouneed.client.compose.platform.ComposeFrameDriver;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the Compose UI update + record phase at the very start of each frame (world-render
 * stage), so the GUI stage only replays recorded draw commands. See {@link ComposeFrameDriver}.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void ae2$composeFrameStart(float partialTicks, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        ComposeFrameDriver.INSTANCE.onGameFrameStart(partialTicks);
    }
}
