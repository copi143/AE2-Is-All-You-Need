package allyouneed.mixin.client;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loads built-in crafting storage models for this mod's namespace
 * (AE2's hook only accepts the {@code ae2} namespace).
 */
@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    @Shadow
    protected abstract void cacheAndQueueDependencies(ResourceLocation id, UnbakedModel unbakedModel);

    @Inject(method = "loadModel", at = @At("HEAD"), cancellable = true)
    private void allyouneed$loadBuiltInModel(ResourceLocation id, CallbackInfo ci) {
        UnbakedModel model = BuiltInModelHooksAccessor.getBuiltInModels().get(id);
        if (model != null) {
            cacheAndQueueDependencies(id, model);
            ci.cancel();
        }
    }
}
