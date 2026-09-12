package allyouneed.mixin.forge;

import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forge 的 {@link RegistryObject} 在 4 参构造器里为每个实例 eager new 一个
 * {@code new Throwable("Calling Site from mod: " + modid)}，只为了在极少触发的
 * "registry 不存在" 错误分支里当 cause 用。
 *
 * <p>实测一个 GTCEu 客户端堆里有 16233 个内容完全相同的
 * "Calling Site from mod: gtceu" Throwable，每个还带一份几十帧的
 * stackTrace 数组。构造期的 {@code fillInStackTrace} 全堆栈 walk 既慢又占内存。
 *
 * <p>本 mixin 把那次 NEW 重定向到廉价工厂：默认不再捕获调用栈（消息保留并
 * intern），错误分支本身的 {@code IllegalStateException} 仍带完整栈。
 * 需要原行为时加 JVM 参数 {@code -Dallyouneed.registryCallSite.full=true}。
 */
@Mixin(RegistryObject.class)
public abstract class RegistryObjectMixin {

    /**
     * 是否恢复 Forge 原行为（构造期捕获完整调用栈）。默认 false。
     * 只在启动时读一次，避免每次分配都查系统属性。
     */
    @Unique
    private static final boolean ALLYOUNEED$FULL_CALL_SITE =
            Boolean.getBoolean("allyouneed.registryCallSite.full");

    @Redirect(
            method = "<init>(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;Z)V",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/lang/Throwable;")
    )
    private Throwable allyouneed$lazyCallerStack(String message) {
        if (ALLYOUNEED$FULL_CALL_SITE) {
            return new Throwable(message);
        }
        // writableStackTrace=false：跳过 fillInStackTrace 的全栈 walk；
        // 同一 modid 的消息内容完全相同，intern 掉重复 String。
        return new NoStackTrace(message.intern());
    }

    /**
     * 不捕获调用栈的 Throwable 占位。{@code super(msg, null, true, false)}
     * 是 protected，只能经由子类调用。
     */
    private static final class NoStackTrace extends Throwable {
        NoStackTrace(String message) {
            super(message, null, true, false);
        }
    }
}
