package allyouneed.mixin.ae2;

import appeng.me.GridConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GridConnection.class, remap = false)
public interface GridConnectionAccessor {
    @Accessor("usedChannels")
    void allyouneed$setUsedChannels(int value);
}
