package gsonfast.pojos;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 模拟 ServerStatus$Serializer 形状的树构建序列化器：root + 嵌套 JsonObject +
 * JsonArray + ctx.serialize + 条件属性。scenario() 自包含，便于隔离 ClassLoader 端到端。
 */
public class FakeTreeSerializer implements JsonSerializer<FakeTreeSerializer.Status> {
    public static final class Status {
        public final String motd;
        public final Integer max;
        public final Boolean online;
        public final String[] sample;
        public final JsonElement extra;

        public Status(String motd, Integer max, Boolean online, String[] sample, JsonElement extra) {
            this.motd = motd;
            this.max = max;
            this.online = online;
            this.sample = sample;
            this.extra = extra;
        }
    }

    @Override
    public JsonElement serialize(Status status, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject root = new JsonObject();
        if (status.motd != null) {
            root.addProperty("motd", status.motd);
        }
        if (status.max != null) {
            root.addProperty("max", status.max);
        }
        if (status.online != null) {
            root.addProperty("online", status.online);
        }
        if (status.extra != null) {
            root.add("extra", status.extra);
        }
        if (status.sample != null) {
            JsonArray array = new JsonArray();
            for (String name : status.sample) {
                JsonObject player = new JsonObject();
                player.addProperty("name", name);
                player.addProperty("id", name.length());
                array.add(player);
            }
            root.add("sample", array);
        }
        root.add("ctx", context.serialize(status.motd));
        return root;
    }

    /** 白名单外用法（size/entrySet），用于验证变换器拒绝改写 */
    public JsonElement serializeBad(Status status, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject root = new JsonObject();
        root.addProperty("motd", status.motd == null ? "" : status.motd);
        if (root.size() > 0 && !root.entrySet().isEmpty()) {
            root.addProperty("size", root.size());
        }
        return root;
    }

    public static List<String> scenario() {
        com.google.gson.Gson gson = new GsonBuilder()
                .registerTypeAdapter(Status.class, new FakeTreeSerializer())
                .create();
        List<String> out = new ArrayList<>();
        out.add(gson.toJson(new Status("hello", 20, true, new String[]{"a", "bb"}, null)));
        out.add(gson.toJson(new Status(null, null, null, null, null)));
        out.add(gson.toJson(new Status("", 0, false, new String[]{}, new JsonObject())));
        JsonObject nested = new JsonObject();
        nested.addProperty("k", "v");
        JsonArray arr = new JsonArray();
        arr.add(1);
        arr.add("two");
        nested.add("arr", arr);
        out.add(gson.toJson(new Status("x\"y\nz", -1, null, new String[]{""}, nested)));
        out.add(gson.toJson(new Status("nullSample", 1, true, null, com.google.gson.JsonNull.INSTANCE)));
        return out;
    }

    public JsonElement serializeCast(JsonElement input) {
        JsonObject fresh = new JsonObject();
        fresh.addProperty("fresh", 1);
        ((JsonObject) input).addProperty("external", 2);
        return fresh;
    }

    public JsonElement serializeInstanceOf(JsonElement input) {
        JsonObject fresh = new JsonObject();
        fresh.addProperty("object", input instanceof JsonObject);
        return fresh;
    }

    public JsonElement serializeArray(JsonElement input) {
        JsonObject fresh = new JsonObject();
        Object[] objects = new JsonObject[1];
        objects[0] = fresh;
        return (JsonElement) objects[0];
    }

    public JsonElement serializeLambda(JsonElement input) {
        JsonObject fresh = new JsonObject();
        Runnable update = () -> fresh.addProperty("updated", true);
        update.run();
        return fresh;
    }
}
