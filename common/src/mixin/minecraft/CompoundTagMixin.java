package allyouneed.mixin.minecraft;

import allyouneed.api.ICompoundTagMixin;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

@Mixin(CompoundTag.class)
@SuppressWarnings("AddedMixinMembersNamePattern")
public abstract class CompoundTagMixin implements ICompoundTagMixin {

    @Unique
    private String key1;
    @Unique
    private String key2;
    @Unique
    private String key3;
    @Unique
    private String key4;

    @Unique
    private Tag value1;
    @Unique
    private Tag value2;
    @Unique
    private Tag value3;
    @Unique
    private Tag value4;

    @Final
    @Shadow
    @Mutable
    private Map<String, Tag> tags;

    @Redirect(method = "<init>()V", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"))
    private static HashMap<String, Tag> initWithNull() {
        return null;
    }

    @Shadow
    @SuppressWarnings("RedundantThrows")
    private static void writeNamedTag(String name, Tag tag, DataOutput output) throws IOException {
        throw new AssertionError();
    }

    @Override
    public String getKey1() {
        return key1;
    }

    @Override
    public String getKey2() {
        return key2;
    }

    @Override
    public String getKey3() {
        return key3;
    }

    @Override
    public String getKey4() {
        return key4;
    }

    @Override
    public Tag getValue1() {
        return value1;
    }

    @Override
    public Tag getValue2() {
        return value2;
    }

    @Override
    public Tag getValue3() {
        return value3;
    }

    @Override
    public Tag getValue4() {
        return value4;
    }

    @Shadow
    public abstract int size();

    @Unique
    private int $$findKey(String key) {
        if (key1 != null && key1.equals(key)) return 1;
        if (key2 != null && key2.equals(key)) return 2;
        if (key3 != null && key3.equals(key)) return 3;
        if (key4 != null && key4.equals(key)) return 4;
        return 0;
    }

    @Unique
    private int $$findEmptySlot() {
        if (value1 == null) return 1;
        if (value2 == null) return 2;
        if (value3 == null) return 3;
        if (value4 == null) return 4;
        return 0;
    }

    @Unique
    private String $$getSlotKey(int slot) {
        return switch (slot) {
            case 1 -> key1;
            case 2 -> key2;
            case 3 -> key3;
            case 4 -> key4;
            default -> null;
        };
    }

    @Unique
    private Tag $$getSlotValue(int slot) {
        return switch (slot) {
            case 1 -> value1;
            case 2 -> value2;
            case 3 -> value3;
            case 4 -> value4;
            default -> null;
        };
    }

    @Unique
    private void $$setSlot(int slot, String key, Tag value) {
        switch (slot) {
            case 1 -> {
                key1 = key;
                value1 = value;
            }
            case 2 -> {
                key2 = key;
                value2 = value;
            }
            case 3 -> {
                key3 = key;
                value3 = value;
            }
            case 4 -> {
                key4 = key;
                value4 = value;
            }
        }
    }

    @Unique
    private void $$clearSlot(int slot) {
        switch (slot) {
            case 1 -> {
                key1 = null;
                value1 = null;
            }
            case 2 -> {
                key2 = null;
                value2 = null;
            }
            case 3 -> {
                key3 = null;
                value3 = null;
            }
            case 4 -> {
                key4 = null;
                value4 = null;
            }
        }
    }

    @Unique
    private void $$clearSlots() {
        key1 = null;
        key2 = null;
        key3 = null;
        key4 = null;
        value1 = null;
        value2 = null;
        value3 = null;
        value4 = null;
    }

    @Unique
    private Map<String, Tag> $$toMap() {
        Map<String, Tag> map = Maps.newHashMap();
        if (value1 != null) map.put(key1, value1);
        if (value2 != null) map.put(key2, value2);
        if (value3 != null) map.put(key3, value3);
        if (value4 != null) map.put(key4, value4);
        return map;
    }

    @Unique
    private void $$materializeMap() {
        if (tags != null) throw new IllegalStateException();
        tags = $$toMap();
        $$clearSlots();
    }

    @Unique
    @SuppressWarnings("DuplicatedCode")
    private void $$dematerializeMap() {
        if (tags == null) throw new IllegalStateException();
        if (tags.size() > 4) throw new IllegalStateException();
        if (!tags.isEmpty()) {
            var entries = tags.entrySet().iterator();
            if (entries.hasNext()) {
                var e = entries.next();
                key1 = e.getKey();
                value1 = e.getValue();
            }
            if (entries.hasNext()) {
                var e = entries.next();
                key2 = e.getKey();
                value2 = e.getValue();
            }
            if (entries.hasNext()) {
                var e = entries.next();
                key3 = e.getKey();
                value3 = e.getValue();
            }
            if (entries.hasNext()) {
                var e = entries.next();
                key4 = e.getKey();
                value4 = e.getValue();
            }
            if (entries.hasNext()) throw new IllegalStateException();
        }
        tags = null;
    }

    /* ============================================================ * write * ============================================================ */

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void $write(DataOutput output, CallbackInfo ci) throws IOException {
        if (tags == null) {
            if (value1 != null) writeNamedTag(key1, value1, output);
            if (value2 != null) writeNamedTag(key2, value2, output);
            if (value3 != null) writeNamedTag(key3, value3, output);
            if (value4 != null) writeNamedTag(key4, value4, output);
            output.writeByte(0);
            ci.cancel();
        }
    }

    @SuppressWarnings("DuplicatedCode")
    @Inject(method = "sizeInBytes", at = @At("HEAD"), cancellable = true)
    private void $sizeInBytes(CallbackInfoReturnable<Integer> cir) {
        if (tags == null) {
            int cnt = 0;
            int size = 48;
            if (value1 != null) {
                size += 2 * key1.length() + value1.sizeInBytes();
                cnt++;
            }
            if (value2 != null) {
                size += 2 * key2.length() + value2.sizeInBytes();
                cnt++;
            }
            if (value3 != null) {
                size += 2 * key3.length() + value3.sizeInBytes();
                cnt++;
            }
            if (value4 != null) {
                size += 2 * key4.length() + value4.sizeInBytes();
                cnt++;
            }
            size += (28 + 36) * cnt;
            cir.setReturnValue(size);
        }
    }

    @Inject(method = "getAllKeys", at = @At("HEAD"), cancellable = true)
    private void $getAllKeys(CallbackInfoReturnable<Set<String>> cir) {
        if (tags == null) {
            var result = new ObjectOpenHashSet<String>(4);
            if (key1 != null) result.add(key1);
            if (key2 != null) result.add(key2);
            if (key3 != null) result.add(key3);
            if (key4 != null) result.add(key4);
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "size", at = @At("HEAD"), cancellable = true)
    private void $size(CallbackInfoReturnable<Integer> cir) {
        if (tags == null) {
            int size = 0;
            if (value1 != null) size++;
            if (value2 != null) size++;
            if (value3 != null) size++;
            if (value4 != null) size++;
            cir.setReturnValue(size);
        }
    }

    @Redirect(method = {"put", "putByte", "putShort", "putInt", "putLong", "putUUID", "putFloat", "putDouble", "putString", "putByteArray*", "putIntArray*", "putLongArray*", "putBoolean"}, at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object $$put(Map<String, Tag> instance, Object k, Object v) {
        String key = (String) k;
        Tag value = (Tag) v;
        if (tags == null) {
            int existing = $$findKey(key);
            if (existing != 0) {
                Tag old = $$getSlotValue(existing);
                $$setSlot(existing, key, value);
                return old;
            }
            int empty = $$findEmptySlot();
            if (empty != 0) {
                $$setSlot(empty, key, value);
                return null;
            }
            $$materializeMap();
        }
        return tags.put(key, value);
    }

    @Redirect(method = {"get", "getTagType", "getByte", "getShort", "getInt", "getLong", "getFloat", "getDouble", "getString", "getByteArray", "getIntArray", "getLongArray", "getCompound", "getList", "createReport"}, at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object $$get(Map<String, Tag> instance, Object k) {
        String key = (String) k;
        if (tags == null) {
            int slot = $$findKey(key);
            return slot == 0 ? null : $$getSlotValue(slot);
        } else {
            return tags.get(key);
        }
    }

    @Inject(method = "contains(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private void $contains(String key, CallbackInfoReturnable<Boolean> cir) {
        if (tags == null) {
            cir.setReturnValue($$findKey(key) != 0);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void $remove(String key, CallbackInfo ci) {
        if (tags == null) {
            int slot = $$findKey(key);
            if (slot != 0) {
                $$clearSlot(slot);
            }
            ci.cancel();
        }
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void $remove$tail(String key, CallbackInfo ci) {
        if (tags != null && tags.size() < 4) {
            $$dematerializeMap();
        }
    }

    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void $isEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (tags == null) {
            cir.setReturnValue(value1 == null && value2 == null && value3 == null && value4 == null);
        }
    }

    @Inject(method = "copy()Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"), cancellable = true)
    private void $copy(CallbackInfoReturnable<CompoundTag> cir) {
        if (tags == null) {
            CompoundTag copy = new CompoundTag();
            if (value1 != null) copy.put(key1, value1.copy());
            if (value2 != null) copy.put(key2, value2.copy());
            if (value3 != null) copy.put(key3, value3.copy());
            if (value4 != null) copy.put(key4, value4.copy());
            cir.setReturnValue(copy);
        }
    }

    @Inject(method = "equals", at = @At("HEAD"), cancellable = true)
    private void equals(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (this == other) {
            cir.setReturnValue(true);
        } else if (!(other instanceof CompoundTag)) {
            cir.setReturnValue(false);
        } else if (this.size() != ((CompoundTag) other).size()) {
            cir.setReturnValue(false);
        } else if (this.tags == null) {
            if (this.key1 != null && !Objects.equals(((CompoundTag) other).get(this.key1), this.value1)) {
                cir.setReturnValue(false);
            } else if (this.key2 != null && !Objects.equals(((CompoundTag) other).get(this.key2), this.value2)) {
                cir.setReturnValue(false);
            } else if (this.key3 != null && !Objects.equals(((CompoundTag) other).get(this.key3), this.value3)) {
                cir.setReturnValue(false);
            } else if (this.key4 != null && !Objects.equals(((CompoundTag) other).get(this.key4), this.value4)) {
                cir.setReturnValue(false);
            } else {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "hashCode", at = @At("HEAD"), cancellable = true)
    private void hashCode(CallbackInfoReturnable<Integer> cir) {
        if (tags == null) {
            int hash = 0;
            if (key1 != null) hash += Objects.hashCode(key1) ^ Objects.hashCode(value1);
            if (key2 != null) hash += Objects.hashCode(key2) ^ Objects.hashCode(value2);
            if (key3 != null) hash += Objects.hashCode(key3) ^ Objects.hashCode(value3);
            if (key4 != null) hash += Objects.hashCode(key4) ^ Objects.hashCode(value4);
            cir.setReturnValue(hash);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Inject(method = "merge", at = @At("HEAD"), cancellable = true)
    private void $merge(CompoundTag other, CallbackInfoReturnable<CompoundTag> cir) {
        Map<String, Tag> otherTags = ((CompoundTagAccessor) other).getTags();
        ICompoundTagMixin otherMixin = (ICompoundTagMixin) other;
        if (otherTags == null) {
            if (otherMixin.getKey1() != null) $$merge(otherMixin.getKey1(), otherMixin.getValue1());
            if (otherMixin.getKey2() != null) $$merge(otherMixin.getKey2(), otherMixin.getValue2());
            if (otherMixin.getKey3() != null) $$merge(otherMixin.getKey3(), otherMixin.getValue3());
            if (otherMixin.getKey4() != null) $$merge(otherMixin.getKey4(), otherMixin.getValue4());
            cir.setReturnValue((CompoundTag) (Object) this);
        } else if (this.tags == null && otherTags.size() > 3) {
            $$materializeMap();
        }
    }

    @Unique
    private void $$merge(String key, Tag value) {
        CompoundTag self = (CompoundTag) (Object) this;
        if (value.getId() == Tag.TAG_COMPOUND) {
            if (self.contains(key, Tag.TAG_COMPOUND)) {
                CompoundTag compoundTag = self.getCompound(key);
                compoundTag.merge((CompoundTag) value);
            } else {
                self.put(key, value.copy());
            }
        } else {
            self.put(key, value.copy());
        }
    }

    @Inject(method = "entries", at = @At("HEAD"), cancellable = true)
    private void entries(CallbackInfoReturnable<Map<String, Tag>> cir) {
        if (tags == null) {
            cir.setReturnValue(Collections.unmodifiableMap($$toMap()));
        }
    }

    @Inject(method = "accept(Lnet/minecraft/nbt/StreamTagVisitor;)Lnet/minecraft/nbt/StreamTagVisitor$ValueResult;", at = @At("HEAD"), cancellable = true)
    private void accept(StreamTagVisitor visitor, CallbackInfoReturnable<StreamTagVisitor.ValueResult> cir) {
        if (tags == null) {
            if (value1 != null && $$visitSlot(visitor, cir, key1, value1) != null) return;
            if (value2 != null && $$visitSlot(visitor, cir, key2, value2) != null) return;
            if (value3 != null && $$visitSlot(visitor, cir, key3, value3) != null) return;
            if (value4 != null && $$visitSlot(visitor, cir, key4, value4) != null) return;
            cir.setReturnValue(visitor.visitContainerEnd());
        }
    }

    @Unique
    private StreamTagVisitor.ValueResult $$visitSlot(StreamTagVisitor visitor, CallbackInfoReturnable<StreamTagVisitor.ValueResult> cir, String key, Tag tag) {
        StreamTagVisitor.ValueResult result = $$visitSlot(visitor, key4, value4);
        if (result != null) cir.setReturnValue(result);
        return result;
    }

    @Unique
    private StreamTagVisitor.ValueResult $$visitSlot(StreamTagVisitor visitor, String key, Tag tag) {
        StreamTagVisitor.EntryResult result = visitor.visitEntry(tag.getType());
        switch (result) {
            case HALT:
                return StreamTagVisitor.ValueResult.HALT;
            case BREAK:
                return visitor.visitContainerEnd();
            case SKIP:
                return null;
        }
        result = visitor.visitEntry(tag.getType(), key);
        switch (result) {
            case HALT:
                return StreamTagVisitor.ValueResult.HALT;
            case BREAK:
                return visitor.visitContainerEnd();
            case SKIP:
                return null;
        }
        StreamTagVisitor.ValueResult valueResult = tag.accept(visitor);
        return switch (valueResult) {
            case HALT -> StreamTagVisitor.ValueResult.HALT;
            case BREAK -> visitor.visitContainerEnd();
            default -> null;
        };
    }
}
