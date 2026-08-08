package allyouneed.mixin.client;

import allyouneed.util.id.mac.MacNbt;
import allyouneed.util.id.mac.MacTooltipTexts;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Shows stored MAC addresses on wrench-dismantled device item stacks.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void allyouneed$appendMacTooltip(
            Player player,
            TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> cir
    ) {
        ItemStack self = (ItemStack) (Object) this;
        if (!self.hasTag() || !self.getTag().contains(MacNbt.ITEM_TAG)) {
            return;
        }
        List<Component> lines = cir.getReturnValue();
        if (lines != null) {
            MacTooltipTexts.appendItemTooltip(self, lines);
        }
    }
}
