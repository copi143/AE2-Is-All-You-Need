package allyouneed.mixin.emi;

import allyouneed.client.integration.emi.fold.EmiFoldUi;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.SidebarPanel.class, remap = false)
public abstract class EmiSidebarNavigationMixin {
    @Unique private SidebarType ae2inya$previousType;

    // 单参数注入不会生成 Args$N 辅助类，避免 Forge 模块类加载器无法解析合成类。
    @ModifyArg(method = "drawBackground", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/EmiRenderHelper;drawNinePatch(Ldev/emi/emi/runtime/EmiDrawContext;Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"), index = 3, remap = false)
    private int ae2inya$backgroundY(int y) {
        return y - EmiFoldUi.reservedHeight((EmiScreenManager.SidebarPanel) (Object) this);
    }

    @ModifyArg(method = "drawBackground", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/EmiRenderHelper;drawNinePatch(Ldev/emi/emi/runtime/EmiDrawContext;Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V"), index = 5, remap = false)
    private int ae2inya$backgroundHeight(int height) {
        return height + EmiFoldUi.reservedHeight((EmiScreenManager.SidebarPanel) (Object) this);
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void ae2inya$toolbar(EmiDrawContext context, int mx, int my, float delta, CallbackInfo ci) {
        EmiFoldUi.draw((EmiScreenManager.SidebarPanel) (Object) this, context, mx, my);
    }

    @Inject(method = "getBounds", at = @At("RETURN"), cancellable = true, remap = false)
    private void ae2inya$bounds(CallbackInfoReturnable<Bounds> cir) {
        cir.setReturnValue(EmiFoldUi.extendBounds((EmiScreenManager.SidebarPanel) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "setSidebarPage", at = @At("HEAD"), remap = false)
    private void ae2inya$beforeType(int page, CallbackInfo ci) {
        ae2inya$previousType = ((EmiScreenManager.SidebarPanel) (Object) this).getType();
    }

    @Inject(method = "setSidebarPage", at = @At("RETURN"), remap = false)
    private void ae2inya$afterType(int page, CallbackInfo ci) {
        SidebarType current = ((EmiScreenManager.SidebarPanel) (Object) this).getType();
        if (current != ae2inya$previousType && (current == SidebarType.INDEX || ae2inya$previousType == SidebarType.INDEX)) {
            EmiScreenManagerAccessor.ae2inya$setLastWidth(-1);
        }
    }
}
