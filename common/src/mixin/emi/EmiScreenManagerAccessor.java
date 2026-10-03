package allyouneed.mixin.emi;

import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EmiScreenManager.class, remap = false)
public interface EmiScreenManagerAccessor {
    @Accessor("lastWidth")
    static void ae2inya$setLastWidth(int value) { throw new AssertionError(); }
}
