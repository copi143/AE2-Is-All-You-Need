package allyouneed.mixin.client.minecraft;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * vanilla 的 ItemProperties.register 是 private 且 common 模块编译于 vanilla jar
 * （Forge 的公开重载是 Forge patch，Fabric 上不存在），用 Invoker 打通双加载器。
 */
@Mixin(ItemProperties.class)
public interface ItemPropertiesAccessor {

    @Invoker("register")
    static void invokeRegister(Item item, ResourceLocation id, ClampedItemPropertyFunction property) {
        throw new AssertionError();
    }
}
