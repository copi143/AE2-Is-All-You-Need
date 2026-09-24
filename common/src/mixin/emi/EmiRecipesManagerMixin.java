package allyouneed.mixin.emi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiStackList;

import allyouneed.util.CsrIndex;

/**
 * bake 完成后把 {@code byInput}/{@code byOutput} 的每 key 一个 {@code List<EmiRecipe>} 拍平为
 * CSR 索引（offsets[] + recipeIds[]），常驻数十万条目的 ArrayList/LinkedHashSet 包装全部消除；
 * key 侧仍用原 {@link EmiStackList.ComparisonHashStrategy} 的 stack→intId 映射，查询语义与顺序不变。
 * 返回的列表是不可变随机访问视图，与原来的 {@code stream().toList()} 语义一致。
 * 原 byInput/byOutput 字段在 CSR 建成后置空释放。
 */
@Mixin(targets = "dev.emi.emi.registry.EmiRecipes$Manager", remap = false)
public abstract class EmiRecipesManagerMixin {

    @Shadow
    private Map<EmiStack, List<EmiRecipe>> byInput;

    @Shadow
    private Map<EmiStack, List<EmiRecipe>> byOutput;

    @Shadow
    @Final
    private List<EmiRecipe> recipes;

    @Unique
    private Object2IntMap<EmiStack> ae2isallyouneed$inputStackIds;

    @Unique
    private CsrIndex ae2isallyouneed$inputCsr;

    @Unique
    private Object2IntMap<EmiStack> ae2isallyouneed$outputStackIds;

    @Unique
    private CsrIndex ae2isallyouneed$outputCsr;

    @Inject(method = "<init>(Ljava/util/List;Ljava/util/Map;Ljava/util/List;Z)V", at = @At("RETURN"))
    private void ae2isallyouneed$csrify(List<EmiRecipeCategory> categories,
            Map<EmiRecipeCategory, List<EmiIngredient>> workstations, List<EmiRecipe> recipesParam,
            boolean doSort, CallbackInfo ci) {
        var recipeIds = new Reference2IntOpenHashMap<EmiRecipe>(this.recipes.size());
        for (int i = 0; i < this.recipes.size(); i++) {
            recipeIds.put(this.recipes.get(i), i);
        }

        var input = ae2isallyouneed$build(byInput, recipeIds);
        ae2isallyouneed$inputStackIds = input.stackIds;
        ae2isallyouneed$inputCsr = input.csr;
        byInput = null;

        var output = ae2isallyouneed$build(byOutput, recipeIds);
        ae2isallyouneed$outputStackIds = output.stackIds;
        ae2isallyouneed$outputCsr = output.csr;
        byOutput = null;
    }

    @Unique
    private static Holder ae2isallyouneed$build(Map<EmiStack, List<EmiRecipe>> map,
            Reference2IntOpenHashMap<EmiRecipe> recipeIds) {
        var stackIds = new Object2IntOpenCustomHashMap<EmiStack>(new EmiStackList.ComparisonHashStrategy());
        stackIds.defaultReturnValue(-1);
        var perKey = new ArrayList<int[]>(map.size());
        int sid = 0;
        for (var entry : map.entrySet()) {
            stackIds.put(entry.getKey(), sid++);
            var list = entry.getValue();
            int[] ids = new int[list.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = recipeIds.getInt(list.get(i));
            }
            perKey.add(ids);
        }
        return new Holder(stackIds, CsrIndex.Companion.build(perKey));
    }

    @Unique
    private static final class Holder {
        final Object2IntMap<EmiStack> stackIds;
        final CsrIndex csr;

        Holder(Object2IntMap<EmiStack> stackIds, CsrIndex csr) {
            this.stackIds = stackIds;
            this.csr = csr;
        }
    }

    /**
     * @author ae2isallyouneed
     * @reason CSR 索引查询；语义与顺序与原实现一致。
     */
    @Overwrite
    public List<EmiRecipe> getRecipesByInput(EmiStack stack) {
        var ids = ae2isallyouneed$inputStackIds;
        if (ids == null) {
            return List.of();
        }
        int sid = ids.getInt(stack);
        if (sid < 0) {
            return List.of();
        }
        return ae2isallyouneed$inputCsr.slice(sid, recipes::get);
    }

    /**
     * @author ae2isallyouneed
     * @reason CSR 索引查询；语义与顺序与原实现一致。
     */
    @Overwrite
    public List<EmiRecipe> getRecipesByOutput(EmiStack stack) {
        var ids = ae2isallyouneed$outputStackIds;
        if (ids == null) {
            return List.of();
        }
        int sid = ids.getInt(stack);
        if (sid < 0) {
            return List.of();
        }
        return ae2isallyouneed$outputCsr.slice(sid, recipes::get);
    }
}
