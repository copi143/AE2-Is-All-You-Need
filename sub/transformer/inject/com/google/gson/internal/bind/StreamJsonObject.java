package com.google.gson.internal.bind;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JsonObject 的乐观流式委托者（JsonObject 是 final 无法继承，这里继承 JsonElement）。
 *
 * 由 JsonStreamTransformer 把声明的 serialize 方法体内的 `new JsonObject()` 改写成本类：
 * add/addProperty 只把 (key, value) 追加到平坦的操作列表（避开 LinkedTreeMap 的红黑树
 * 插入与 Entry 分配），写出时经 {@link #writeHook}（由 Streams.write 的钩子调用）直接
 * 流式回放，不建树。
 *
 * 任何按树方式消费本对象的读取（getAsJsonObject / deepCopy / 嵌在真实树里被 gson 的
 * JSON_ELEMENT 适配器写出等）都会触发 {@link #materialize()} 生成真正的 JsonObject，
 * 语义与原版完全一致；物化过于频繁的序列化器由熔断器切换为 eager 模式（构造时就持有
 * 真实 JsonObject，行为与原版逐字节一致），保证优化永不成为净损失。
 */
public final class StreamJsonObject extends JsonElement {
    private static final boolean ENABLED = !"false".equals(System.getProperty("allyouneed.jsonstream"));

    /** 同一 tag 物化达到该次数后，后续实例直接进入 eager 模式 */
    private static final long EAGER_THRESHOLD = 64;

    private static final ConcurrentHashMap<String, AtomicLong> MATERIALIZATIONS = new ConcurrentHashMap<>();
    private static final AtomicLong STREAMED = new AtomicLong();
    private static final AtomicLong FALLBACKS = new AtomicLong();

    private static final Object LOG = initLog();

    private final String tag;
    /** 非 null 表示 eager（熔断/禁用）或已物化；此后所有操作直接委托给它 */
    private JsonObject eager;
    /** 平坦操作列表：[key0, value0, key1, value1, ...]，value ∈ JsonNull/String/Number/Boolean/Character/JsonElement */
    private ArrayList<Object> ops;

    public StreamJsonObject(String tag) {
        this.tag = tag;
        if (!ENABLED || tripped(tag)) {
            this.eager = new JsonObject();
        } else {
            this.ops = new ArrayList<>(16);
        }
    }

    // ===== JsonObject 兼容的构建 API（变换器按同名同描述符改写调用） =====

    public void add(String property, JsonElement value) {
        if (property == null) throw new NullPointerException("key == null");
        JsonObject m = objectForProperty(property);
        if (m != null) {
            m.add(property, value);
            return;
        }
        ops.add(property);
        ops.add(value == null ? JsonNull.INSTANCE : value);
    }

    public void addProperty(String property, String value) {
        addValue(property, value);
    }

    public void addProperty(String property, Number value) {
        addValue(property, value);
    }

    public void addProperty(String property, Boolean value) {
        addValue(property, value);
    }

    public void addProperty(String property, Character value) {
        addValue(property, value);
    }

    private void addValue(String property, Object value) {
        if (property == null) throw new NullPointerException("key == null");
        JsonObject m = objectForProperty(property);
        if (m != null) {
            putPrimitive(m, property, value);
            return;
        }
        ops.add(property);
        ops.add(value == null ? JsonNull.INSTANCE : value);
    }

    private JsonObject objectForProperty(String property) {
        if (eager != null) return eager;
        // A repeated key must replace its value without moving its insertion position.
        // Keep the usual small-object path allocation-free; let JsonObject handle updates.
        for (int i = 0; i < ops.size(); i += 2) {
            if (property.equals(ops.get(i))) return materialize();
        }
        return null;
    }

    private static void putPrimitive(JsonObject m, String property, Object value) {
        if (value == null) {
            m.add(property, JsonNull.INSTANCE);
        } else if (value instanceof String) {
            m.addProperty(property, (String) value);
        } else if (value instanceof Number) {
            m.addProperty(property, (Number) value);
        } else if (value instanceof Boolean) {
            m.addProperty(property, (Boolean) value);
        } else {
            m.addProperty(property, (Character) value);
        }
    }

    // ===== 写出 =====

    /**
     * Streams.write 的方法头钩子：是本类实例则流式写出并返回 true，否则返回 false 走原版。
     * 预检失败（FallbackSignal，保证尚未产生输出）退回到物化树写出。
     */
    public static boolean writeHook(JsonElement element, JsonWriter writer) throws IOException {
        if (!(element instanceof StreamJsonObject)) return false;
        StreamJsonObject self = (StreamJsonObject) element;
        try {
            self.streamTo(writer);
            STREAMED.incrementAndGet();
        } catch (FallbackSignal signal) {
            FALLBACKS.incrementAndGet();
            writeTree(self.materialize(), writer);
        }
        return true;
    }

    private void streamTo(JsonWriter writer) throws IOException {
        JsonObject m = eager;
        if (m != null) {
            writeTree(m, writer);
            return;
        }
        validate(ops, 0);
        writer.beginObject();
        for (int i = 0; i < ops.size(); i += 2) {
            writer.name((String) ops.get(i));
            writeValue(ops.get(i + 1), writer);
        }
        writer.endObject();
    }

    /** 预检：只检查本层操作列表的值类型；嵌套 StreamJsonObject 在自身 streamTo 里再预检 */
    private static void validate(ArrayList<Object> ops, int depth) {
        for (int i = 1; i < ops.size(); i += 2) {
            Object v = ops.get(i);
            if (v == null
                    || v instanceof String || v instanceof Number || v instanceof Boolean
                    || v instanceof Character || v instanceof JsonElement) {
                continue;
            }
            throw new FallbackSignal("unsupported op value " + v.getClass());
        }
    }

    private static void writeValue(Object v, JsonWriter writer) throws IOException {
        if (v == null || v == JsonNull.INSTANCE) {
            writer.nullValue();
        } else if (v instanceof String) {
            writer.value((String) v);
        } else if (v instanceof Number) {
            writer.value((Number) v);
        } else if (v instanceof Boolean) {
            writer.value(((Boolean) v).booleanValue());
        } else if (v instanceof Character) {
            writer.value(v.toString());
        } else if (v instanceof StreamJsonObject) {
            ((StreamJsonObject) v).streamTo(writer);
        } else if (v instanceof JsonElement) {
            writeTree((JsonElement) v, writer);
        } else {
            throw new FallbackSignal("unsupported op value " + v.getClass());
        }
    }

    /**
     * 复刻 gson 的 TypeAdapters.JSON_ELEMENT.write（com.google.gson.internal.Streams 在
     * Forge 的命名模块里未导出，不能直接委托；语义逐分支对齐，包括外来子类的异常）。
     */
    private static void writeTree(JsonElement element, JsonWriter writer) throws IOException {
        if (element == null || element.isJsonNull()) {
            writer.nullValue();
            return;
        }
        if (element instanceof StreamJsonObject) {
            ((StreamJsonObject) element).streamTo(writer);
            return;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                writer.value(primitive.getAsNumber());
            } else if (primitive.isBoolean()) {
                writer.value(primitive.getAsBoolean());
            } else {
                writer.value(primitive.getAsString());
            }
            return;
        }
        if (element.isJsonArray()) {
            writer.beginArray();
            for (JsonElement child : element.getAsJsonArray()) {
                writeTree(child, writer);
            }
            writer.endArray();
            return;
        }
        if (element.isJsonObject()) {
            writer.beginObject();
            for (Map.Entry<String, JsonElement> en : element.getAsJsonObject().entrySet()) {
                writer.name(en.getKey());
                writeTree(en.getValue(), writer);
            }
            writer.endObject();
            return;
        }
        throw new IllegalArgumentException("Couldn't write " + element.getClass());
    }

    // ===== 物化（语义回退的基石） =====

    public JsonObject materialize() {
        JsonObject m = eager;
        if (m != null) return m;
        ArrayList<Object> snapshot = ops;
        m = new JsonObject();
        for (int i = 0; i < snapshot.size(); i += 2) {
            Object v = snapshot.get(i + 1);
            if (v instanceof JsonElement) {
                m.add((String) snapshot.get(i), (JsonElement) v);
            } else {
                putPrimitive(m, (String) snapshot.get(i), v);
            }
        }
        eager = m;
        ops = null;
        countMaterialization(tag);
        return m;
    }

    // ===== JsonElement API =====

    @Override
    public boolean isJsonObject() {
        return true;
    }

    @Override
    public JsonObject getAsJsonObject() {
        return materialize();
    }

    @Override
    public JsonElement deepCopy() {
        return materialize().deepCopy();
    }

    @Override
    public String toString() {
        try {
            StringWriter stringWriter = new StringWriter();
            JsonWriter jsonWriter = new JsonWriter(stringWriter);
            jsonWriter.setLenient(true);
            streamTo(jsonWriter);
            return stringWriter.toString();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o == this || materialize().equals(o);
    }

    @Override
    public int hashCode() {
        return materialize().hashCode();
    }

    // ===== 熔断器 =====

    private static boolean tripped(String tag) {
        AtomicLong counter = MATERIALIZATIONS.get(tag);
        return counter != null && counter.get() >= EAGER_THRESHOLD;
    }

    private static void countMaterialization(String tag) {
        long n = MATERIALIZATIONS.computeIfAbsent(tag, k -> new AtomicLong()).incrementAndGet();
        if (n == EAGER_THRESHOLD) {
            log("json stream: serializer '" + tag + "' keeps materializing; switching to eager mode");
        }
    }

    // ===== 诊断（测试与运维） =====

    public static long streamedCount() {
        return STREAMED.get();
    }

    public static long fallbackCount() {
        return FALLBACKS.get();
    }

    public static long materializationCount(String tag) {
        AtomicLong counter = MATERIALIZATIONS.get(tag);
        return counter == null ? 0 : counter.get();
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
