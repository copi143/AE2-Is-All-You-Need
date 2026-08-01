package allyouneed.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import allyouneed.async.AsyncChannelNodeHolder;
import appeng.me.GridNode;

@Mixin(value = GridNode.class, remap = false)
public abstract class GridNodeMixin implements AsyncChannelNodeHolder {

    @Unique
    private int allyouneed$asyncSwallowedChannels = 0;

    @Override
    public void setAsyncSwallowedChannels(int channels) {
        this.allyouneed$asyncSwallowedChannels = channels;
    }

    @Override
    public int getAsyncSwallowedChannels() {
        return this.allyouneed$asyncSwallowedChannels;
    }
}
