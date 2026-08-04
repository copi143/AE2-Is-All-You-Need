package allyouneed.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import allyouneed.async.AsyncChannelNodeHolder;
import allyouneed.mac.IMacAddressHolder;
import allyouneed.mac.MacAddress;
import allyouneed.mac.MacAddressRegistry;
import allyouneed.mac.MacNbt;
import allyouneed.mac.MacPolicy;
import appeng.api.networking.IGridNode;
import appeng.me.GridNode;
import net.minecraft.nbt.CompoundTag;

@Mixin(value = GridNode.class, remap = false)
public abstract class GridNodeMixin implements AsyncChannelNodeHolder, IMacAddressHolder {

    @Unique
    private int allyouneed$asyncSwallowedChannels = 0;

    @Unique
    private long allyouneed$macAddress = MacAddress.NONE;

    @Override
    public void setAsyncSwallowedChannels(int channels) {
        this.allyouneed$asyncSwallowedChannels = channels;
    }

    @Override
    public int getAsyncSwallowedChannels() {
        return this.allyouneed$asyncSwallowedChannels;
    }

    @Override
    public long getMacAddress() {
        return this.allyouneed$macAddress;
    }

    @Override
    public void setMacAddress(long mac) {
        this.allyouneed$macAddress = MacAddress.normalize(mac);
    }

    @Inject(method = "loadFromNBT", at = @At("TAIL"))
    private void allyouneed$loadMac(String name, CompoundTag nodeDataContainer, CallbackInfo ci) {
        IGridNode self = (IGridNode) (Object) this;
        if (!MacPolicy.shouldHaveMac(self)) {
            this.allyouneed$macAddress = MacAddress.NONE;
            return;
        }
        if (nodeDataContainer.get(name) instanceof CompoundTag nodeTag) {
            long mac = MacNbt.readNodeMac(nodeTag);
            if (MacAddress.isValid(mac)) {
                this.allyouneed$macAddress = mac;
            }
        }
    }

    @Inject(method = "saveToNBT", at = @At("TAIL"))
    private void allyouneed$saveMac(String name, CompoundTag nodeData, CallbackInfo ci) {
        IGridNode self = (IGridNode) (Object) this;
        if (!MacPolicy.shouldHaveMac(self) || !MacAddress.isValid(this.allyouneed$macAddress)) {
            if (nodeData.get(name) instanceof CompoundTag nodeTag) {
                MacNbt.writeNodeMac(nodeTag, MacAddress.NONE);
            }
            return;
        }
        if (nodeData.get(name) instanceof CompoundTag nodeTag) {
            MacNbt.writeNodeMac(nodeTag, this.allyouneed$macAddress);
        } else {
            CompoundTag nodeTag = new CompoundTag();
            MacNbt.writeNodeMac(nodeTag, this.allyouneed$macAddress);
            nodeData.put(name, nodeTag);
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void allyouneed$unregisterMac(CallbackInfo ci) {
        if (MacAddress.isValid(this.allyouneed$macAddress)) {
            MacAddressRegistry.unregister(this.allyouneed$macAddress, (IGridNode) (Object) this);
        }
    }

    @Inject(method = "markReady", at = @At("TAIL"))
    private void allyouneed$registerMac(CallbackInfo ci) {
        IGridNode self = (IGridNode) (Object) this;
        if (!MacPolicy.shouldHaveMac(self)) {
            this.allyouneed$macAddress = MacAddress.NONE;
            return;
        }
        if (MacAddress.isValid(this.allyouneed$macAddress)) {
            if (!MacAddressRegistry.register(this.allyouneed$macAddress, self)) {
                // Collision: clear so ManagedGridNode.ensureAndBind can reallocate.
                // markReady may run before Managed create TAIL; create path is authoritative.
                MacAddressRegistry.unregister(this.allyouneed$macAddress, self);
                this.allyouneed$macAddress = MacAddress.NONE;
            }
        }
    }
}
