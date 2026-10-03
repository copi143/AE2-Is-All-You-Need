package allyouneed.mixin.emi;

import allyouneed.client.integration.emi.fold.EmiFoldGroups;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 将 EMI 侧边栏索引（INDEX，含搜索结果）的同类物品折叠为单组显示。
 *
 * <p>只改显示层：EMI 的索引、搜索与配方匹配仍基于原始成员；搜索变窄后
 * 组内成员不足阈值会自动散开。其它侧边栏（收藏/历史/可合成）不受影响。
 */
@Mixin(value = EmiScreenManager.ScreenSpace.class, remap = false)
public abstract class EmiScreenSpaceFoldMixin {

    @Shadow(remap = false)
    public abstract SidebarType getType();

    @Inject(method = "getStacks", at = @At("RETURN"), cancellable = true, remap = false)
    private void ae2inyaFoldGroups(CallbackInfoReturnable<List> cir) {
        if (getType() != SidebarType.INDEX) {
            return;
        }
        List<?> raw = cir.getReturnValue();
        if (raw == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<EmiIngredient> folded = EmiFoldGroups.fold((EmiScreenManager.ScreenSpace) (Object) this, (List<EmiIngredient>) raw);
        if (folded != raw) {
            cir.setReturnValue(folded);
        }
    }
}
