package allyouneed.mixin.ae2;

import allyouneed.util.id.mac.MacHosts;
import allyouneed.util.id.mac.MacNbt;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.parts.PartPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = PartPlacement.class, remap = false)
public abstract class PartPlacementMixin {

    @Inject(method = "placePart", at = @At("RETURN"))
    private static void allyouneed$importMacOnPartPlace(
            Player player,
            Level level,
            IPartItem<?> partItem,
            CompoundTag configTag,
            BlockPos pos,
            Direction side,
            CallbackInfoReturnable<IPart> cir
    ) {
        IPart part = cir.getReturnValue();
        if (part == null || level.isClientSide()) {
            return;
        }
        Map<String, Long> macs = MacNbt.getMacs(configTag);
        if (!macs.isEmpty()) {
            MacHosts.applyMacs(part, macs);
        }
    }
}
