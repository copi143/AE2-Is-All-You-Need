package allyouneed.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensure pseudo patterns are pushed as simple item emissions to external inventories.
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin {

    @Inject(method = "pushPattern", at = @At("HEAD"), remap = false)
    private void allyouneed$handlePseudo(IPatternDetails patternDetails, KeyCounter[] inputHolder, CallbackInfoReturnable<Boolean> cir) {
        if (patternDetails instanceof allyouneed.pattern.pseudo.AEPseudoPattern) {
            // We just let the normal flow continue; the pseudo pattern reports supportsPushInputsToExternalInventory()=true
            // and has empty outputs, so it will be pushed to adapters.
            // Nothing special to do here besides ensuring it is not filtered out.
        }
    }
}
