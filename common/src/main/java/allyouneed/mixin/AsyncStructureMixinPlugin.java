package allyouneed.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

/**
 * Soft-mixin guard for the GTCEu-targeting mixins in this config. GTCEu is an optional runtime
 * dependency on Fabric, so its classes may be absent when the config is processed. Vetoing the
 * mixin up front lets the config load cleanly without the target-class resolution failing, while
 * the mixins still apply whenever the classes exist.
 *
 * <p>The presence probe must not load the class: {@code Class.forName} here would register the
 * target with the classloader before the MixinTransformer has committed this config, so the
 * already-loaded class is never transformed and the mixin silently never applies. A classpath
 * resource lookup checks presence without loading.
 */
public class AsyncStructureMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!targetClassName.startsWith("com.gregtechceu.gtceu.")) return true;
        String resource = targetClassName.replace('.', '/') + ".class";
        return getClass().getClassLoader().getResource(resource) != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
