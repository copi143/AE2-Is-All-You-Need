package allyouneed.mixin.minecraft;

import allyouneed.api.IFluidKeyHolder;
import appeng.api.stacks.AEFluidKey;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(Fluid.class)
public abstract class FluidMixin implements IFluidKeyHolder {

    @Unique
    @Nullable
    private AEFluidKey allyouneed$plainFluidKey;

    @Override
    public @Nullable AEFluidKey getPlainFluidKey() {
        return this.allyouneed$plainFluidKey;
    }

    @Override
    public void setPlainFluidKey(@Nullable AEFluidKey key) {
        this.allyouneed$plainFluidKey = key;
    }
}
