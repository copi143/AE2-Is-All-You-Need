package allyouneed.mixin.ae2;

import allyouneed.api.KeyLocation;
import allyouneed.api.KeyLocations;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(value = KeyCounter.class, remap = false)
public abstract class KeyCounterMixin implements KeyLocations {

    @Unique
    @Nullable
    private Map<AEKey, List<KeyLocation>> allyouneed$locations;

    @Override
    public List<KeyLocation> getLocations(AEKey key) {
        if (this.allyouneed$locations == null || key == null) {
            return List.of();
        }
        var list = this.allyouneed$locations.get(key);
        return list == null ? List.of() : list;
    }

    @Override
    public void replaceLocations(Map<AEKey, List<KeyLocation>> locations) {
        if (locations == null || locations.isEmpty()) {
            this.allyouneed$locations = null;
            return;
        }
        var copy = new Reference2ObjectOpenHashMap<AEKey, List<KeyLocation>>(locations.size());
        for (var entry : locations.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.allyouneed$locations = copy.isEmpty() ? null : copy;
    }

    @Override
    public void addLocations(Map<AEKey, List<KeyLocation>> locations) {
        if (locations == null || locations.isEmpty()) {
            return;
        }
        if (this.allyouneed$locations == null) {
            this.allyouneed$locations = new Reference2ObjectOpenHashMap<>();
        }
        for (var entry : locations.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            this.allyouneed$locations.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
        }
    }

    @Override
    public Map<AEKey, List<KeyLocation>> copyLocations() {
        if (this.allyouneed$locations == null || this.allyouneed$locations.isEmpty()) {
            return Map.of();
        }
        var copy = new Reference2ObjectOpenHashMap<AEKey, List<KeyLocation>>(this.allyouneed$locations.size());
        for (var entry : this.allyouneed$locations.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return copy;
    }

    @Inject(method = "addAll", at = @At("TAIL"))
    private void allyouneed$addLocations(KeyCounter other, CallbackInfo ci) {
        this.addLocations(((KeyLocations) (Object) other).copyLocations());
    }

    @Inject(method = {"clear", "reset"}, at = @At("HEAD"))
    private void allyouneed$clearLocations(CallbackInfo ci) {
        this.allyouneed$locations = null;
    }

    @Inject(method = "remove(Lappeng/api/stacks/AEKey;)J", at = @At("TAIL"))
    private void allyouneed$removeLocations(AEKey key, CallbackInfoReturnable<Long> cir) {
        if (this.allyouneed$locations != null) {
            this.allyouneed$locations.remove(key);
            if (this.allyouneed$locations.isEmpty()) {
                this.allyouneed$locations = null;
            }
        }
    }
}
