package allyouneed.mixin.ae2;

import allyouneed.api.IMacAddressHolder;
import allyouneed.api.IManagedMacAddressHolder;
import allyouneed.netaddr.mac.MacAddress;
import allyouneed.netaddr.mac.MacAddressRegistry;
import allyouneed.netaddr.mac.MacNbt;
import allyouneed.netaddr.mac.MacPolicy;
import appeng.me.GridNode;
import appeng.me.ManagedGridNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(value = ManagedGridNode.class, remap = false)
public abstract class ManagedGridNodeMixin implements IManagedMacAddressHolder {

    @Shadow
    private String tagName;

    @Shadow
    private GridNode node;

    @Unique
    private long allyouneed$macAddress = MacAddress.NONE;

    @Override
    public long getMacAddress() {
        return this.allyouneed$macAddress;
    }

    @Override
    public void setMacAddress(long mac) {
        this.allyouneed$macAddress = MacAddress.normalize(mac);
        if (this.node instanceof IMacAddressHolder holder) {
            holder.setMacAddress(this.allyouneed$macAddress);
        }
    }

    @Override
    public String getMacTagName() {
        return this.tagName;
    }

    @Inject(method = "loadFromNBT", at = @At("HEAD"))
    private void allyouneed$loadMac(CompoundTag tag, CallbackInfo ci) {
        if (tag == null) {
            return;
        }
        if (tag.get(this.tagName) instanceof CompoundTag nodeTag) {
            long mac = MacNbt.readNodeMac(nodeTag);
            if (MacAddress.isValid(mac)) {
                this.allyouneed$macAddress = mac;
            }
        }
    }

    @Inject(method = "saveToNBT", at = @At("TAIL"))
    private void allyouneed$saveMac(CompoundTag tag, CallbackInfo ci) {
        if (this.node != null && !MacPolicy.shouldHaveMac(this.node)) {
            this.allyouneed$macAddress = MacAddress.NONE;
            if (tag != null && tag.get(this.tagName) instanceof CompoundTag nodeTag) {
                MacNbt.writeNodeMac(nodeTag, MacAddress.NONE);
            }
            return;
        }
        if (this.node instanceof IMacAddressHolder holder) {
            long nodeMac = holder.getMacAddress();
            if (MacAddress.isValid(nodeMac)) {
                this.allyouneed$macAddress = nodeMac;
            }
        }
        if (!MacAddress.isValid(this.allyouneed$macAddress) || tag == null) {
            return;
        }
        CompoundTag nodeTag;
        if (tag.get(this.tagName) instanceof CompoundTag existing) {
            nodeTag = existing;
        } else {
            nodeTag = new CompoundTag();
            tag.put(this.tagName, nodeTag);
        }
        MacNbt.writeNodeMac(nodeTag, this.allyouneed$macAddress);
    }

    @Inject(method = "create", at = @At("TAIL"))
    private void allyouneed$afterCreate(Level level, BlockPos blockPos, CallbackInfo ci) {
        if (this.node == null) {
            return;
        }
        MacAddressRegistry.ensureAndBind(this, this.node);
    }
}
