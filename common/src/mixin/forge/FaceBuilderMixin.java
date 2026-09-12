package allyouneed.mixin.forge;

import net.minecraftforge.client.model.generators.ModelBuilder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Forge 运行时模型生成器的面纹理变量名去重。
 *
 * <p>{@code FaceBuilder.texture}（如 {@code "#side"}、{@code "#side_secondary"}、
 * {@code "side"}）在运行时模型生成（KubeJS/EMI 等）中被大量重复持有，
 * 堆里有上万份内容相同的短串。取值集合很小，直接 {@link String#intern}。
 * setter 保证非空（{@code Preconditions.checkNotNull}），这里仍做空判断，
 * 与上游行为保持一致。
 */
@Mixin(value = ModelBuilder.ElementBuilder.FaceBuilder.class, remap = false)
public abstract class FaceBuilderMixin {

    @ModifyVariable(method = "texture", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private String allyouneed$internTexture(String texture) {
        return texture == null ? null : texture.intern();
    }
}
