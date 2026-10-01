package com.google.gson.internal.bind;

/**
 * {@link FastAdapterGenerator} 生成适配器所需的单个字段元数据。
 * 顶级类而非嵌套类：注入类通过 Lookup.defineClass 逐个定义，
 * 嵌套类的校验帧会在宿主/成员定义顺序上互相死锁（NCDFE），顶级类无此问题。
 */
final class FastAdapterEntry {
    final String jsonName;
    final Class<?> owner;
    final Class<?> fieldType;
    final boolean nullSkip;
    final boolean methodHandle;
    final int dataIndex;

    FastAdapterEntry(String jsonName, Class<?> owner, Class<?> fieldType, boolean nullSkip, boolean methodHandle, int dataIndex) {
        this.jsonName = jsonName;
        this.owner = owner;
        this.fieldType = fieldType;
        this.nullSkip = nullSkip;
        this.methodHandle = methodHandle;
        this.dataIndex = dataIndex;
    }
}
