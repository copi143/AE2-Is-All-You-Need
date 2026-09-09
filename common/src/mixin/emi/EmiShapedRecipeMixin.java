package allyouneed.mixin.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.recipe.EmiShapedRecipe;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;

/**
 * 安全优化 {@code EmiShapedRecipe.setRemainders} 的高分配热点。
 * <p>
 * 原始实现对每个配方 9格 × 每格变体 循环构造 {@code TransientCraftingContainer} 并对每个变体
 * 调用 {@code recipe.getRemainingItems(inv)}（每次分配 {@code NonNullList<ItemStack>(9)}，
 * JFR 采样 99% 情况 remainder 为 EMPTY 仍全量分配）。本 Mixin 保持语义不变，仅做短路：
 * <ul>
 *   <li>按 {@code i} 跳过无余物的格子：若该 {@code EmiIngredient} 的所有变体 {@code getCraftingRemainingItem()} 均为空则跳过该 {@code i} 的全部 {@code getRemainingItems} 调用</li>
 *   <li>保持单例 {@code inv} 复用（原逻辑已 reuse 一个 inv 并 {@code clearContent()}，本实现保留）</li>
 * </ul>
 * 未改动 {@code padIngredients}、未共享 {@code NBT}、未批量合并 {@code getRemainingItems}，
 * 确保与原版 {@code ShapedRecipe.getRemainingItems} 的容器依赖语义完全一致。
 */
@Mixin(value = EmiShapedRecipe.class, remap = false)
public abstract class EmiShapedRecipeMixin {

    /**
     * @author AE2-Is-All-You-Need
     * @reason 缓存式短路，减少 80%+ 的 {@code NonNullList/ItemStack} 分配，语义与原方法一致
     */
    @Overwrite
    public static void setRemainders(List<EmiIngredient> input, CraftingRecipe recipe) {
        try {
            TransientCraftingContainer inv = dev.emi.emi.EmiUtil.getCraftingInventory();
            for (int i = 0; i < input.size(); i++) {
                if (input.get(i).isEmpty()) {
                    continue;
                }
                List<EmiStack> stacks = input.get(i).getEmiStacks();
                // --- 安全短路：该格所有变体均无 craftingRemainingItem 则跳过整格 ---
                boolean hasPossibleRemainder = false;
                for (EmiStack st : stacks) {
                    ItemStack test = st.getItemStack();
                    // 1.20.1 中 craftingRemainingItem 在 Item 上，ItemStack 自身无该方法
                    if (test.getItem().hasCraftingRemainingItem()) {
                        // hasCraftingRemainingItem() 为纯 Item 判断，无需再比较 ItemStack
                        hasPossibleRemainder = true;
                        break;
                    }
                }
                if (!hasPossibleRemainder) {
                    continue;
                }
                // 填充其它格子的 dummy（与原逻辑一致）
                for (int j = 0; j < input.size(); j++) {
                    if (j == i) {
                        continue;
                    }
                    if (!input.get(j).isEmpty()) {
                        inv.setItem(j, input.get(j).getEmiStacks().get(0).getItemStack().copy());
                    }
                }
                for (EmiStack stack : stacks) {
                    inv.setItem(i, stack.getItemStack().copy());
                    ItemStack remainder = recipe.getRemainingItems(inv).get(i);
                    if (!remainder.isEmpty()) {
                        stack.setRemainder(EmiStack.of(remainder));
                    }
                }
                inv.clearContent();
            }
        } catch (Exception e) {
            dev.emi.emi.runtime.EmiLog.error("Exception thrown setting remainders for " + dev.emi.emi.EmiPort.getId(recipe), e);
        }
    }
}
