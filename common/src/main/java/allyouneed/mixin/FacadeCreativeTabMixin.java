package allyouneed.mixin;

import appeng.core.FacadeCreativeTab;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;

/**
 * AE2 的 Facade 标签页正常注册（appeng:facades）。
 * 我们不取消注册，原版注册逻辑不动。
 * 侧边栏的 "AE2" 分类会通过命名空间过滤，只显示 appeng 的标签页（包括 facades）。
 * 这样 facade 只在 AE2 分类下可见，不出现在原版分类的过滤视图中。
 */
@Mixin(value = FacadeCreativeTab.class, remap = false)
public abstract class FacadeCreativeTabMixin {
    // 不再取消 init，让 AE2 正常注册 facades tab
}
