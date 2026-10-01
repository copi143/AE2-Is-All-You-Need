package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.ObjectConstructor;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Field;

/**
 * 委托 {@link ReflectiveTypeAdapterFactory}：create 结果经 {@link GsonFastPath#wrap}
 * 尝试替换为生成的专用适配器。委托的 create 返回 null 时（非 Object 类型）原样透传。
 */
public final class FastFactory implements TypeAdapterFactory {
    private final ReflectiveTypeAdapterFactory delegate;
    private final ConstructorConstructor constructors;

    /**
     * 让 gson 模块补读到 ASM 模块的读边（Module.addReads 有 caller 检查，必须由 gson
     * 模块内的代码发起）。放在本类而非 GsonFastPath：本类 clinit 为空，初始化安全；
     * GsonFastPath 的 clinit 会做元数据反射（加载 RTAF 内部类进而连带 nest host RTAF
     * 本体），其初始化必须推迟到 RTAF 先发替换完成之后。
     */
    public static void prepareModules() {
        try {
            Module self = FastFactory.class.getModule();
            Module asm = Class.forName("org.objectweb.asm.ClassWriter", false, FastFactory.class.getClassLoader()).getModule();
            if (self != null && asm != null && self != asm && !self.canRead(asm)) self.addReads(asm);
        } catch (Throwable ignored) {
            // ASM 不可用时 FastAdapterGenerator 会链接失败，wrap 内统一回退
        }
    }

    FastFactory(ReflectiveTypeAdapterFactory delegate) throws NoSuchFieldException {
        this.delegate = delegate;
        Field f = ReflectiveTypeAdapterFactory.class.getDeclaredField("constructorConstructor");
        f.setAccessible(true);
        try {
            this.constructors = (ConstructorConstructor) f.get(delegate);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        TypeAdapter<T> adapter = delegate.create(gson, type);
        if (adapter == null) return null;
        ObjectConstructor<T> ctor = constructors.get(type);
        return (TypeAdapter<T>) GsonFastPath.wrap(adapter, gson, type.getRawType(), ctor);
    }
}
