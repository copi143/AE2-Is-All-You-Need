package allyouneed.mixin.gtceu;

import com.gregtechceu.gtceu.client.util.StaticFaceBakery;
import com.gregtechceu.gtceu.core.IGTBakedQuad;

import net.minecraft.client.renderer.block.model.BakedQuad;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GTCEu 静态烘焙路径的内存优化（{@link StaticFaceBakery#bakeQuad} 是 GTCEu
 * 机器/管道模型的主要烘焙入口）。
 *
 * <ol>
 *   <li><b>textureKey intern</b>：{@code face.texture}（如 {@code "#side"}、
 *   {@code "layer1"}）每个模型解析出一份实例，堆里有 2~3 万份内容相同的短串。
 *   取值集合很小，直接 {@link String#intern}。</li>
 *   <li><b>vertices 去重</b>：每次 bake 都新建 {@code int[32]}，实测两组各 9559 份
 *   内容完全一致。返回前按内容 canonical，命中则用共享数组重建 quad
 *   （约 144B/个）。烘焙返回的 quad 被当作不可变使用，见
 *   {@link QuadVerticesInterner} 的安全说明。</li>
 * </ol>
 */
@Mixin(value = StaticFaceBakery.class, remap = false)
public abstract class StaticFaceBakeryMixin {

    @ModifyArg(
            method = "bakeQuad",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/model/BakedQuad;gtceu$setTextureKey(Ljava/lang/String;)Lnet/minecraft/client/renderer/block/model/BakedQuad;",
                    remap = false
            ),
            index = 0
    )
    private static String allyouneed$internTextureKey(String key) {
        return key == null ? null : key.intern();
    }

    @Inject(method = "bakeQuad", remap = false, at = @At("RETURN"), cancellable = true)
    private static void allyouneed$dedupVertices(CallbackInfoReturnable<BakedQuad> cir) {
        BakedQuad quad = cir.getReturnValue();
        if (quad == null) {
            return;
        }
        int[] vertices = quad.getVertices();
        int[] shared = QuadVerticesInterner.intern(vertices);
        if (shared == vertices) {
            return;
        }
        BakedQuad deduped = new BakedQuad(
                shared,
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade(),
                quad.hasAmbientOcclusion());
        String key = ((IGTBakedQuad) (Object) quad).gtceu$getTextureKey();
        ((IGTBakedQuad) (Object) deduped).gtceu$setTextureKey(key);
        cir.setReturnValue(deduped);
    }
}
