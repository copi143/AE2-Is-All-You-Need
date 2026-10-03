package allyouneed.mixin.emi;

import allyouneed.client.integration.emi.fold.EmiFoldGroups;
import allyouneed.client.integration.emi.fold.EmiFoldUi;
import allyouneed.client.integration.emi.fold.FoldedGroupIngredient;
import allyouneed.client.integration.emi.fold.GroupedIngredient;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 标题栏导航以及 INDEX 分组交互：小组原位展开/收起，大组钻入下一层。
 *
 * <p>必须挂在 {@code mouseClicked}（按下瞬间）而非 {@code stackInteraction}：
 * 后者还会被 {@code keyPressed}（如单按 Alt）和 {@code mouseReleased}
 * 调用，只看 Alt 状态会在“先点左键、松开前按下 Alt”或“只按 Alt”时误触。
 * 此处消费点击后 {@code pressedStack} 不会被赋值，抬起路径自然无事可做，
 * 不会二次触发也不会弹出配方页。
 */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiMouseClickFoldMixin {

    @Shadow(remap = false)
    public static boolean isDisabled() {
        return false;
    }

    @Shadow(remap = false)
    public static EmiStackInteraction getHoveredStack(int mouseX, int mouseY, boolean notClick) {
        return EmiStackInteraction.EMPTY;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ae2inyaFoldToggle(
        double mouseX,
        double mouseY,
        int button,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (isDisabled()) {
            return;
        }
        if (EmiFoldUi.click((int) mouseX, (int) mouseY, button)) {
            EmiScreenManager.pressedStack = EmiStack.EMPTY;
            cir.setReturnValue(true);
            return;
        }
        // 只处理左右键按下；其它按键与纯键盘事件一律放行
        if (button != 0 && button != 1) {
            return;
        }
        if (!Screen.hasAltDown()) {
            return;
        }
        EmiStackInteraction interaction = getHoveredStack((int) mouseX, (int) mouseY, false);
        if (!(interaction instanceof EmiScreenManager.SidebarEmiStackInteraction sidebar)
                || sidebar.getType() != SidebarType.INDEX) {
            return;
        }
        EmiIngredient hovered = interaction.getStack();
        var panel = EmiFoldUi.INSTANCE.panelFor(sidebar.space);
        if (panel != null && (
                hovered instanceof FoldedGroupIngredient group && EmiFoldGroups.INSTANCE.enter(panel, group)
                || hovered instanceof GroupedIngredient member && EmiFoldGroups.INSTANCE.collapse(panel, member))) {
            EmiScreenManager.pressedStack = EmiStack.EMPTY;
            cir.setReturnValue(true);
        }
    }
}
