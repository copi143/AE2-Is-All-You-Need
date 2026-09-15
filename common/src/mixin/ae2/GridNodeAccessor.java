package allyouneed.mixin.ae2;

import appeng.me.GridNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GridNode.class, remap = false)
public interface GridNodeAccessor {
    @Accessor("usedChannels")
    void allyouneed$setUsedChannels(int value);
}
