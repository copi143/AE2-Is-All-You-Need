package com.google.gson.internal.bind;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ClassValue lets an application's class loader and its cached layouts unload together. */
final class FastAdapterCache extends ClassValue<Map<List<Object>, MethodHandle>> {
    @Override
    protected Map<List<Object>, MethodHandle> computeValue(Class<?> type) {
        return new HashMap<>();
    }
}
