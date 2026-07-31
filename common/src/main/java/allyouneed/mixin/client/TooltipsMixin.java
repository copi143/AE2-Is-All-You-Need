package allyouneed.mixin.client;

import allyouneed.util.IecFormat;
import appeng.core.localization.Tooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 {@code BYTE_NUMS} only has 4 entries and OOBs once storage reaches ~1 TiB+.
 * Route large / unbounded values through {@link IecFormat}.
 */
@Mixin(value = Tooltips.class, remap = false)
public class TooltipsMixin {

    @Final
    @Shadow
    public static Style NUMBER_TEXT;

    @Final
    @Shadow
    public static Style UNIT_TEXT;

    @Inject(method = "ofBytes", at = @At("HEAD"), cancellable = true)
    private static void allyouneed$ofBytes(long number, CallbackInfoReturnable<MutableComponent> cir) {
        if (number == Long.MAX_VALUE || number < 0) {
            cir.setReturnValue(Component.literal("∞").withStyle(NUMBER_TEXT));
            return;
        }
        // AE2 BYTE_NUMS length is 4; values needing index >= 4 crash (roughly >= 1 TiB * 1000)
        long gib = 1024L * 1024L * 1024L;
        if (number >= gib * 1000L) {
            cir.setReturnValue(formatLabel(IecFormat.formatBytes(number)));
        }
    }

    private static MutableComponent formatLabel(String label) {
        // Split trailing unit letter(s) if present (e.g. "256t", "1g", "512k")
        int split = label.length();
        while (split > 0 && Character.isLetter(label.charAt(split - 1))) {
            split--;
        }
        if (split <= 0 || split >= label.length()) {
            return Component.literal(label).withStyle(NUMBER_TEXT);
        }
        return Component.literal(label.substring(0, split)).withStyle(NUMBER_TEXT).append(Component.literal(label.substring(split)).withStyle(UNIT_TEXT));
    }
}
