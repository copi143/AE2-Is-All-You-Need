package com.google.gson.internal.bind;

import org.objectweb.asm.ClassWriter;

/**
 * COMPUTE_FRAMES 需要解析公共父类，默认实现用当前 ClassLoader 加载类；
 * 生成类的字段声明类可能暂时不可达，失败时降级为 Object（生成的代码不依赖精确合并类型）。
 * 顶级类而非匿名类：见 {@link FastAdapterEntry} 的说明。
 */
final class FrameSafeClassWriter extends ClassWriter {
    FrameSafeClassWriter(int flags) {
        super(flags);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (Throwable t) {
            return "java/lang/Object";
        }
    }
}
