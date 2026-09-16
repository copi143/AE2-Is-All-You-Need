package allyouneed.mixin.minecraft;

import allyouneed.util.inventory.InventoryWatchers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityInventoryMixin {

    @Inject(method = "setChanged()V", at = @At("TAIL"))
    private void allyouneed$notifyInventory(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        var level = self.getLevel();
        if (level == null || level.isClientSide || !(self instanceof Container)) {
            return;
        }
        BlockPos pos = self.getBlockPos();
        InventoryWatchers.notifyPos(level, pos);
        if (self instanceof ChestBlockEntity) {
            var state = self.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                InventoryWatchers.notifyPos(level, pos.relative(ChestBlock.getConnectedDirection(state)));
            }
        }
    }
}
