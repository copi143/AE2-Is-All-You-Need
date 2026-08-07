package allyouneed.mixin.minecraft;

import allyouneed.cell.LightningRodCharge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Charges the energy cell below a lightning rod when the rod is struck.
 * Runtime-gated on GTCEu being loaded in {@link LightningRodCharge}.
 */
@Mixin(LightningRodBlock.class)
public abstract class LightningRodMixin {

    @Inject(method = "onLightningStrike", at = @At("TAIL"))
    private void allyouneed$chargeEnergyCell(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        LightningRodCharge.onLightningStrike(state, level, pos);
    }
}
