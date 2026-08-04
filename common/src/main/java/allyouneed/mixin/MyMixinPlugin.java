package allyouneed.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

/**
 * Generic optional-dependency guard for the mixins in this config.
 *
 * <p>Some mixins target classes of optional runtime dependencies (currently GTCEu: the group
 * pattern mixins in {@code allyouneed.mixin.gtceu}; more may follow). On a platform where such a
 * mod is absent (Fabric never ships GTCEu) the target classes do not exist and the mixins must be
 * skipped, otherwise config processing fails. Vetoing the mixin up front lets the config load
 * cleanly, while the mixins still apply whenever the classes exist.
 *
 * <p>Every target class is probed generically: if its class file is not reachable on the
 * classpath, the mixin is skipped. There is no hard-coded dependency list, so future optional
 * dependencies are handled automatically.
 *
 * <p>The presence probe must not load the class: {@code Class.forName} here would register the
 * target with the classloader before the MixinTransformer has committed this config, so the
 * already-loaded class is never transformed and the mixin silently never applies. A classpath
 * resource lookup checks presence without loading.
 */
public class MyMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
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
