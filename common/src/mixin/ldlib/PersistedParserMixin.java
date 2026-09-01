package allyouneed.mixin.ldlib;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存 {@code Class.getMethods()} 的过滤结果以消除 GTCEu/LDLib 在批量创建 EMI 配方时的反射热点。
 *
 * <p>原始实现位于 {@code com.lowdragmc.lowdraglib.gui.editor.runtime.PersistedParser:81-94}：
 * <pre>
 *   for (Method m : clazz.getMethods()) { if(m.isAnnotationPresent(ConfigSetter.class)) setters.put(...) }
 *   deserializeNBT(tag, setters, clazz.getSuperclass(), object);
 * </pre>
 * 每个 {@code GTEmiRecipe -> GTRecipeWidget -> GTRecipeTypeUI.createEditableUITemplate:187} 都会对
 * {@code WidgetGroup} 继承链的每一级调用 {@code getMethods()}，而 {@code getMethods()} 每次都会
 * {@code Method.copy()} 全量拷贝（JFR 中 56 samples / 1359MB 采样）。对 2000+ 配方重复 5~6 层即上万次拷贝。
 *
 * <p>本 Mixin 拦截 {@code getMethods()} 调用，按 {@code Class} 缓存已过滤的 {@code @ConfigSetter} 方法数组，
 * 行为与原逻辑等价（仅返回已标注的方法，原循环的 {@code !containsKey} 语义在外层保留），无额外副作用。
 * 使用字符串比对注解名避免编译期依赖 LDLib。
 */
@Mixin(targets = "com.lowdragmc.lowdraglib.gui.editor.runtime.PersistedParser", remap = false)
public abstract class PersistedParserMixin {

    @Unique
    private static final Map<Class<?>, Method[]> ae2IsAllYouNeed$setterCache = new ConcurrentHashMap<>();

    @Unique
    private static final String CONFIG_SETTER_NAME = "com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter";

    @Redirect(
            method = "deserializeNBT",
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;getMethods()[Ljava/lang/reflect/Method;"),
            remap = false
    )
    private static Method[] ae2IsAllYouNeed$cachedGetMethods(Class<?> clazz) {
        Method[] cached = ae2IsAllYouNeed$setterCache.get(clazz);
        if (cached != null) {
            return cached;
        }
        // 首次计算：原 getMethods() + 过滤 @ConfigSetter，避免每次全量拷贝
        Method[] all = clazz.getMethods();
        // 快速路径：若无 @ConfigSetter，直接缓存空数组
        int count = 0;
        for (Method m : all) {
            for (Annotation a : m.getAnnotations()) {
                if (CONFIG_SETTER_NAME.equals(a.annotationType().getName())) {
                    count++;
                    break;
                }
            }
        }
        Method[] filtered;
        if (count == 0) {
            filtered = new Method[0];
        } else {
            filtered = new Method[count];
            int idx = 0;
            for (Method m : all) {
                for (Annotation a : m.getAnnotations()) {
                    if (CONFIG_SETTER_NAME.equals(a.annotationType().getName())) {
                        filtered[idx++] = m;
                        break;
                    }
                }
            }
        }
        // putIfAbsent 避免并发重复计算
        Method[] prev = ae2IsAllYouNeed$setterCache.putIfAbsent(clazz, filtered);
        return prev != null ? prev : filtered;
    }
}
