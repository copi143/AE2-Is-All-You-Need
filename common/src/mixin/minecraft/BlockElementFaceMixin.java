package allyouneed.mixin.minecraft;

import net.minecraft.client.renderer.block.model.BlockElementFace;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 模型面纹理变量名的去重。
 *
 * <p>{@link BlockElementFace#texture}（如 {@code "#side"}、{@code "layer1"}）
 * 是所有 {@code BakedQuad} 纹理 key 的源头：原版烘焙和 GTCEu 的
 * StaticFaceBakery 最终都把这个引用存进 quad。每个模型解析/每次烘焙各持一份
 * 实例，堆里有数万份内容相同的短串。取值集合很小（纹理变量名），且只做
 * {@code equals}/{@code charAt} 比较，直接 {@link String#intern}。
 *
 * <p>两个构造器的 String 参数都只有 {@code texture} 一个，
 * {@code ordinal = 0} 无歧义。
 */
@Mixin(BlockElementFace.class)
public abstract class BlockElementFaceMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String allyouneed$internTexture(String texture) {
        return texture == null ? null : texture.intern();
    }
}
