package allyouneed.mixin.ae2;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.SortedMap;

@Mixin(targets = "appeng.api.stacks.FuzzySearch", remap = false)
public interface FuzzySearchAccessor {
    @Invoker("findFuzzy")
    static <T extends SortedMap<K, V>, K, V> T invokeFindFuzzy(T map, AEKey key, FuzzyMode fuzzy) {
        throw new AssertionError();
    }
}
