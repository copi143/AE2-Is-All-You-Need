package allyouneed.mixin.ae2;

import allyouneed.api.GlobalIdHolder;
import allyouneed.util.id.KeyIdRegistry;
import appeng.api.stacks.AEKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Injects a process-global, NBT-excluded integer identity onto every {@link AEKey}
 * for fast search/dedup. The id is assigned lazily on first read from
 * {@link KeyIdRegistry} and cached on the instance; two keys sharing the same
 * item/fluid (different NBT) always resolve to the same id.
 * <p>
 * 向每个 AEKey 注入去 NBT 的进程全局整数身份，用于快速搜索/去重。
 * ID 在首次读取时从 KeyIdRegistry 惰性分配并缓存于实例；
 * 相同物品/流体（不同 NBT）的 AEKey 获得同一个 ID。
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(value = AEKey.class, remap = false)
public abstract class AEKeyMixin implements GlobalIdHolder {

    /**
     * NBT-excluded global id cached on the instance; -1 = not yet assigned.
     */
    @Unique
    private int globalId = -1;

    @Unique
    @Override
    public int getGlobalId() {
        if (this.globalId < 0) {
            this.globalId = KeyIdRegistry.assign((AEKey) (Object) this);
        }
        return this.globalId;
    }

    @Unique
    @Override
    public void invalidateGlobalId() {
        this.globalId = -1;
    }
}
