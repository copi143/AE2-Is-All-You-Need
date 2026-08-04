package allyouneed.mixin.ae2;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import allyouneed.netaddr.mac.MacHosts;
import allyouneed.netaddr.mac.MacNbt;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.parts.PartPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

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
