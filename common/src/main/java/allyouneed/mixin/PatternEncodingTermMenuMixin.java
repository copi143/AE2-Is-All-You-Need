package allyouneed.mixin;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Force the encoding terminal to only support our 3 modes:
 * - MACHINE (we will repurpose or map to an existing slot)
 * - PROCESSING (vanilla processing)
 * - PSEUDO (new)
 *
 * We hide the old CRAFTING / SMITHING / STONECUTTING and prevent encoding them.
 */
@Mixin(PatternEncodingTermMenu.class)
public abstract class PatternEncodingTermMenuMixin {

    @Inject(method = "setMode", at = @At("HEAD"), cancellable = true, remap = false)
    private void allyouneed$forceOurModes(EncodingMode mode, CallbackInfo ci) {
        // Map old modes to our machine or processing
        // We treat CRAFTING as "machine with crafting table" but since machine is chosen in UI, we force to PROCESSING for safety.
        if (mode == EncodingMode.CRAFTING || mode == EncodingMode.SMITHING_TABLE || mode == EncodingMode.STONECUTTING) {
            // Force to PROCESSING to disable old style encoding
            PatternEncodingTermMenu self = (PatternEncodingTermMenu) (Object) this;
            self.setMode(EncodingMode.PROCESSING);
            ci.cancel();
        }
    }

    @Inject(method = "encodePattern", at = @At("HEAD"), cancellable = true, remap = false)
    private void allyouneed$blockOldEncode(CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
        PatternEncodingTermMenu self = (PatternEncodingTermMenu) (Object) this;
        EncodingMode m = self.getMode();
        if (m == EncodingMode.CRAFTING || m == EncodingMode.SMITHING_TABLE || m == EncodingMode.STONECUTTING) {
            // Block encoding of old types
            cir.setReturnValue(null);
        }
    }
}
