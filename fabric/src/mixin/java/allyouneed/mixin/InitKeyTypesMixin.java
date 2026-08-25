package allyouneed.mixin;

import allyouneed.Main;
import appeng.init.client.InitKeyTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InitKeyTypes.class, remap = false)
public class InitKeyTypesMixin {
    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private static void ae2isallyouneed_afterInit(CallbackInfo ci) {
        // 与 Forge 的 RegisterEvent<AEKeyType> 对齐：在 AE2 完成 AEConfig 加载与 ae2:keytypes 注册表创建后立即注册，保证只一次且必成功
        Main.registerAEKeyTypes();
    }
}
