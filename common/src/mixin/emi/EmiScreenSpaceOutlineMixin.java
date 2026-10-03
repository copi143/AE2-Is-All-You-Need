package allyouneed.mixin.emi;

import allyouneed.client.integration.emi.fold.EmiFoldOutline;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 折叠组外轮廓描边：在当前页渲染完毕（batcher 已刷出）后，按同页网格把
 * 相邻同组格的共享边省去，只描合并外圈。展开块内部因此无分隔线。
 */
@Mixin(value = EmiScreenManager.ScreenSpace.class, remap = false)
public abstract class EmiScreenSpaceOutlineMixin {

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void ae2inyaFoldOutline(
        EmiDrawContext context,
        int mouseX,
        int mouseY,
        float delta,
        int startIndex,
        CallbackInfo ci
    ) {
        EmiFoldOutline.drawOutlines(
            (EmiScreenManager.ScreenSpace) (Object) this,
            startIndex,
            context.raw()
        );
    }
}
