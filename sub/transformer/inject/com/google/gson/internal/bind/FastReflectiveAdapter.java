package com.google.gson.internal.bind;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Collections;

/** Preserves Gson's reflective-adapter classification for runtime type selection. */
abstract class FastReflectiveAdapter extends ReflectiveTypeAdapterFactory.Adapter<Object, Object> {
    FastReflectiveAdapter() {
        super(Collections.emptyMap());
    }

    @Override
    public abstract void write(JsonWriter writer, Object value) throws IOException;

    @Override
    public abstract Object read(JsonReader reader) throws IOException;

    // The generated read method owns construction and field dispatch; the inherited
    // reflective read implementation must never be used with the empty boundFields.
    @Override
    Object createAccumulator() {
        throw new AssertionError("Generated read method required");
    }

    @Override
    void readField(Object value, JsonReader reader, ReflectiveTypeAdapterFactory.BoundField field) {
        throw new AssertionError("Generated read method required");
    }

    @Override
    Object finalize(Object value) {
        throw new AssertionError("Generated read method required");
    }
}
