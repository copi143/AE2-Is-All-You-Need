package allyouneed.mixin.ae2;

import allyouneed.util.DismantleFlags;
import allyouneed.util.id.mac.MacHosts;
import allyouneed.util.id.mac.MacNbt;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.util.SettingsFrom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = AEBaseBlockEntity.class, remap = false)
public abstract class AEBaseBlockEntityMixin {

    @Inject(method = "disassembleWithWrench", at = @At("HEAD"))
    private void allyouneed$wrenchStart(Player player, Level level, BlockHitResult hitResult, ItemStack wrench, CallbackInfoReturnable<InteractionResult> cir) {
        DismantleFlags.setWrenchDismantling(true);
    }

    @Inject(method = "disassembleWithWrench", at = @At("RETURN"))
    private void allyouneed$wrenchEnd(Player player, Level level, BlockHitResult hitResult, ItemStack wrench, CallbackInfoReturnable<InteractionResult> cir) {
        DismantleFlags.setWrenchDismantling(false);
    }

    /**
     * Only while {@link #disassembleWithWrench} is running, attach MAC map to dismantle NBT.
     * Ordinary {@code getDrops} also calls exportSettings but without this flag; any MAC is stripped there.
     */
    @Inject(method = "exportSettings", at = @At("TAIL"))
    private void allyouneed$exportMac(SettingsFrom mode, CompoundTag output, @Nullable Player player, CallbackInfo ci) {
        if (mode != SettingsFrom.DISMANTLE_ITEM || output == null || !DismantleFlags.isWrenchDismantling()) {
            return;
        }
        Map<String, Long> macs = MacHosts.collectMacs(this);
        if (!macs.isEmpty()) {
            MacNbt.putMacs(output, macs);
        }
    }
}
