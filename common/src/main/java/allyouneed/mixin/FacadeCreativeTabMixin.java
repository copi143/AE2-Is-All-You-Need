package allyouneed.mixin;

import allyouneed.client.CreativeSubTab;
import appeng.core.FacadeCreativeTab;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static allyouneed.client.CreativeTabsKt.AE2CreativeTab;

@Mixin(value = FacadeCreativeTab.class, remap = false)
public abstract class FacadeCreativeTabMixin {
    @Unique
    private static final CreativeSubTab allyouneed$facades = AE2CreativeTab.subTab("facades");

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private static void allyouneed$init(Registry<CreativeModeTab> registry, CallbackInfo ci) {
        ci.cancel();
    }
}
