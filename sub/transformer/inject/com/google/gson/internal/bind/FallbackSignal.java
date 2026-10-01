package com.google.gson.internal.bind;

/**
 * 乐观流式失败信号：StreamJsonObject.streamTo 在写出任何字节之前的预检阶段抛出，
 * 由 writeHook 捕获并退回到 materialize + 普通树写出。
 *
 * 只在「尚未产生输出」时抛出，因此捕获后重放是安全的。不带栈轨迹，作为廉价控制流使用。
 */
public final class FallbackSignal extends RuntimeException {
    public FallbackSignal(String message) {
        super(message, null, false, false);
    }
}
