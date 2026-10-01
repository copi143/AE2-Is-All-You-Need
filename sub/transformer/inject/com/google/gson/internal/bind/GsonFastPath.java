package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.TypeAdapter;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.ObjectConstructor;
import com.google.gson.reflect.TypeToken;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gson 反射适配器的快速路径入口。由 transformer 在
 * {@code ReflectiveTypeAdapterFactory.create(Gson, TypeToken)} 返回
 * {@code FieldReflectionAdapter} 之前插入的钩子调用。
 *
 * 策略：复用 gson 自己构建好的 {@code boundFields}（字段过滤、@SerializedName、
 * 泛型解析等语义已由 gson 保证正确），只为每个字段把「Field 反射 + Map 查找 +
 * 每次 write 分配 TypeAdapterRuntimeTypeWrapper」替换为生成字节码里的
 * MethodHandle 调用、字符串常量与 switch 分派。任何一步不满足前提都原样返回
 * 传入的反射适配器。
 */
public final class GsonFastPath {
    private static final boolean ENABLED = !"false".equals(System.getProperty("allyouneed.gsonfast"));

    private static final AtomicLong GENERATED = new AtomicLong();
    private static final AtomicLong FALLBACK = new AtomicLong();
    private static final java.util.concurrent.atomic.AtomicBoolean ENGAGED = new java.util.concurrent.atomic.AtomicBoolean();

    /** 最近一次回退原因，仅供诊断/测试 */
    public static volatile Throwable lastFallback;

    private static final Object LOG = initLog();

    private static final Class<?> BF1_CLASS;
    private static final Field VF_TYPE_ADAPTER;
    private static final Field VF_JSON_PRESENT;
    private static final Field VF_CONTEXT;
    private static final Field VF_FIELD_TYPE;
    private static final Field VF_IS_PRIMITIVE;
    private static final Field VF_BLOCK_INACCESSIBLE;
    private static final Field VF_STATIC_FINAL;
    private static final Field VF_ACCESSOR;
    /** BoundField 上的 java.lang.reflect.Field：2.10.1+ 在 BoundField.field，2.10.0 在 $1.val$field */
    private static final Field BF_FIELD;

    static {
        Class<?> c = null;
        Field[] fs = null;
        Field fieldRef = null;
        try {
            c = Class.forName("com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$1");
            fs = new Field[]{
                    c.getDeclaredField("val$typeAdapter"),
                    c.getDeclaredField("val$jsonAdapterPresent"),
                    c.getDeclaredField("val$context"),
                    c.getDeclaredField("val$fieldType"),
                    c.getDeclaredField("val$isPrimitive"),
                    c.getDeclaredField("val$blockInaccessible"),
                    c.getDeclaredField("val$isStaticFinalField"),
                    c.getDeclaredField("val$accessor"),
            };
            try {
                fieldRef = Class.forName("com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$BoundField")
                        .getDeclaredField("field");
            } catch (NoSuchFieldException e) {
                fieldRef = c.getDeclaredField("val$field");
            }
            fs = Arrays.copyOf(fs, fs.length + 1);
            fs[fs.length - 1] = fieldRef;
            for (Field f : fs) f.setAccessible(true);
        } catch (Throwable t) {
            c = null;
            fs = null;
            fieldRef = null;
            log("gson fast path metadata unavailable (" + t + "); fast path disabled");
        }
        BF1_CLASS = c;
        if (fs != null) {
            VF_TYPE_ADAPTER = fs[0];
            VF_JSON_PRESENT = fs[1];
            VF_CONTEXT = fs[2];
            VF_FIELD_TYPE = fs[3];
            VF_IS_PRIMITIVE = fs[4];
            VF_BLOCK_INACCESSIBLE = fs[5];
            VF_STATIC_FINAL = fs[6];
            VF_ACCESSOR = fs[7];
            BF_FIELD = fieldRef;
        } else {
            VF_TYPE_ADAPTER = null;
            VF_JSON_PRESENT = null;
            VF_CONTEXT = null;
            VF_FIELD_TYPE = null;
            VF_IS_PRIMITIVE = null;
            VF_BLOCK_INACCESSIBLE = null;
            VF_STATIC_FINAL = null;
            VF_ACCESSOR = null;
            BF_FIELD = null;
        }
    }

    private GsonFastPath() {
    }

    /**
     * 注入完成后由 RuntimeClasses 反射调用：本类位于 gson 的命名模块内，
     * 而字节码生成依赖 ASM（org.objectweb.asm 模块）。addReads 有 caller 检查，
     * 必须由 gson 模块内的代码发起，不能由 transformer 插件代劳。
     */
    public static void prepareModules() {
        try {
            Module self = GsonFastPath.class.getModule();
            Module asm = Class.forName("org.objectweb.asm.ClassWriter", false, GsonFastPath.class.getClassLoader()).getModule();
            if (self != null && asm != null && self != asm && !self.canRead(asm)) self.addReads(asm);
        } catch (Throwable ignored) {
            // ASM 不可用时 FastAdapterGenerator 会链接失败，wrap 内统一回退
        }
    }

    public static TypeAdapter<?> wrap(TypeAdapter<?> adapter, Gson gson, Class<?> raw, ObjectConstructor<?> ctor) {
        if (!ENABLED || BF1_CLASS == null) return adapter;
        try {
            return tryWrap(adapter, raw, ctor);
        } catch (Throwable t) {
            FALLBACK.incrementAndGet();
            lastFallback = t;
            log("gson fast path: keeping reflective adapter for " + raw.getName() + " (" + t + ")");
            return adapter;
        }
    }

    /**
     * 调用点替换：`new Gson()` → 构造后立即用 {@link FastFactory} 包装其反射工厂。
     */
    public static Gson newGson() {
        return wrapGson(new Gson());
    }

    /**
     * 调用点替换：`builder.create()` → 同上。
     */
    public static Gson create(GsonBuilder builder) {
        return wrapGson(builder.create());
    }

    /**
     * Forge 场景：gson 在 BOOT 层不可被变换，改为在调用点构造 Gson 后
     * 把 factories 里的 ReflectiveTypeAdapterFactory 换成生成适配器的委托工厂。
     * factories 是不可变 List，整体替换；Gson 实例此刻尚未缓存任何适配器。
     */
    private static Gson wrapGson(Gson gson) {
        if (!ENABLED || BF1_CLASS == null) return gson;
        try {
            Field factoriesField = Gson.class.getDeclaredField("factories");
            factoriesField.setAccessible(true);
            List<?> factories = (List<?>) factoriesField.get(gson);
            List<Object> replaced = new ArrayList<>(factories.size());
            boolean found = false;
            for (Object f : factories) {
                if (!found && f instanceof ReflectiveTypeAdapterFactory) {
                    replaced.add(new FastFactory((ReflectiveTypeAdapterFactory) f));
                    found = true;
                } else {
                    replaced.add(f);
                }
            }
            if (found) {
                factoriesField.set(gson, replaced);
                if (ENGAGED.compareAndSet(false, true)) {
                    log("gson fast path engaged: wrapped ReflectiveTypeAdapterFactory in a Gson instance");
                }
            }
        } catch (Throwable t) {
            FALLBACK.incrementAndGet();
            lastFallback = t;
            log("gson fast path: cannot wrap Gson instance (" + t + ")");
        }
        return gson;
    }

    public static long generatedCount() {
        return GENERATED.get();
    }

    public static long fallbackCount() {
        return FALLBACK.get();
    }

    public static Object fget(Field f, Object o) {
        try {
            return f.get(o);
        } catch (Throwable t) {
            throw new JsonIOException(t);
        }
    }

    public static void fset(Field f, Object o, Object v) {
        try {
            f.set(o, v);
        } catch (Throwable t) {
            throw new JsonIOException(t);
        }
    }

    private static TypeAdapter<?> tryWrap(TypeAdapter<?> adapter, Class<?> raw, ObjectConstructor<?> ctor) throws Throwable {
        // 钩子点已保证是 FieldReflectionAdapter；这里只防御性确认是 Adapter 体系
        // （FieldReflectionAdapter 对编译器呈现为 private，无法直接 instanceof）
        if (!(adapter instanceof ReflectiveTypeAdapterFactory.Adapter)) return adapter;
        if (!adapter.getClass().getName().equals("com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$FieldReflectionAdapter")) {
            return adapter;
        }
        Map<String, ReflectiveTypeAdapterFactory.BoundField> boundFields =
                ((ReflectiveTypeAdapterFactory.Adapter<?, ?>) adapter).boundFields;
        if (boundFields == null || boundFields.isEmpty()) return adapter;

        List<FastAdapterEntry> writes = new ArrayList<>();
        List<FastAdapterEntry> reads = new ArrayList<>();
        List<Object> writeData = new ArrayList<>();
        List<Object> readData = new ArrayList<>();

        for (Map.Entry<String, ReflectiveTypeAdapterFactory.BoundField> e : boundFields.entrySet()) {
            ReflectiveTypeAdapterFactory.BoundField bf = e.getValue();
            if (bf == null || bf.getClass() != BF1_CLASS) return adapter;
            if (VF_BLOCK_INACCESSIBLE.getBoolean(bf)) return adapter;
            if (VF_ACCESSOR.get(bf) != null) return adapter;
            if (VF_STATIC_FINAL.getBoolean(bf)) return adapter;
            Field f = (Field) BF_FIELD.get(bf);
            if (f == null) return adapter;
            TypeAdapter<?> fieldAdapter = (TypeAdapter<?>) VF_TYPE_ADAPTER.get(bf);
            if (fieldAdapter == null) return adapter;
            boolean nullSkip = VF_IS_PRIMITIVE.getBoolean(bf);

            if (bf.serialized) {
                TypeAdapter<?> writeAdapter = VF_JSON_PRESENT.getBoolean(bf)
                        ? fieldAdapter
                        : new TypeAdapterRuntimeTypeWrapper<>(
                        (Gson) VF_CONTEXT.get(bf),
                        fieldAdapter,
                        ((TypeToken<?>) VF_FIELD_TYPE.get(bf)).getType());
                Object acc = accessor(f, false);
                if (acc == null) return adapter;
                writes.add(new FastAdapterEntry(
                        e.getKey(), f.getDeclaringClass(), f.getType(), false,
                        acc instanceof MethodHandle, writeData.size()));
                writeData.add(acc);
                writeData.add(writeAdapter);
            }
            if (bf.deserialized) {
                Object acc = accessor(f, true);
                if (acc == null) return adapter;
                reads.add(new FastAdapterEntry(
                        e.getKey(), f.getDeclaringClass(), f.getType(), nullSkip,
                        acc instanceof MethodHandle, readData.size()));
                readData.add(acc);
                readData.add(fieldAdapter);
            }
        }

        byte[] bytes = FastAdapterGenerator.generate(raw, writes, reads);
        Class<?> gen = MethodHandles.lookup().defineHiddenClass(bytes, true).lookupClass();
        TypeAdapter<?> result = (TypeAdapter<?>) gen
                .getDeclaredConstructor(ObjectConstructor.class, Object[].class, Object[].class)
                .newInstance(ctor, writeData.toArray(), readData.toArray());
        GENERATED.incrementAndGet();
        return result;
    }

    private static Object accessor(Field f, boolean setter) {
        boolean accessible;
        try {
            accessible = f.trySetAccessible();
        } catch (Throwable t) {
            accessible = false;
        }
        if (!accessible) {
            try {
                accessible = f.isAccessible();
            } catch (Throwable ignored) {
            }
        }
        if (!accessible) return null;
        try {
            MethodHandles.Lookup pl = MethodHandles.privateLookupIn(f.getDeclaringClass(), MethodHandles.lookup());
            return setter ? pl.unreflectSetter(f) : pl.unreflectGetter(f);
        } catch (Throwable t) {
            return f;
        }
    }

    private static Object initLog() {
        try {
            return org.slf4j.LoggerFactory.getLogger("AE2IsAllYouNeed");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void log(String msg) {
        Object l = LOG;
        if (l instanceof org.slf4j.Logger) {
            ((org.slf4j.Logger) l).warn("[Core] {}", msg);
        } else {
            System.err.println("[AE2IsAllYouNeed] " + msg);
        }
    }
}
