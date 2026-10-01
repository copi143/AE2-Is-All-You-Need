package allyouneed.gsonfast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.BlockDataSource;
import net.minecraft.network.chat.contents.DataSource;
import net.minecraft.network.chat.contents.EntityDataSource;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.StorageDataSource;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ComponentJsonFast {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("allyouneed.componentjson", "true"));
    private static final Gson FALLBACK_GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final StyleAccess STYLE_ACCESS = StyleAccess.resolve();
    private static volatile boolean fallbackLogged;

    private ComponentJsonFast() {
    }

    public static boolean isAvailable() {
        return ENABLED && STYLE_ACCESS != null;
    }

    public static String toJson(Component component) {
        if (component == null) return "null";
        if (!isAvailable()) return fallbackToJson(component);
        try {
            StringWriter sw = new StringWriter(128);
            JsonWriter w = new JsonWriter(sw);
            w.setHtmlSafe(false);
            w.setSerializeNulls(false);
            w.setLenient(true);
            writeComponent(w, component);
            w.flush();
            return sw.toString();
        } catch (Throwable t) {
            logFallback("serialize", t);
            return fallbackToJson(component);
        }
    }

    public static MutableComponent fromJson(String json, boolean lenient) {
        if (json == null || json.isEmpty()) return null;
        if (!isAvailable()) return fallbackFromJson(json, lenient);
        try {
            JsonReader r = new JsonReader(new StringReader(json));
            r.setLenient(lenient);
            if (r.peek() == JsonToken.END_DOCUMENT) return null;
            MutableComponent c = readComponent(r);
            if (r.peek() != JsonToken.END_DOCUMENT) throw new JsonParseException("trailing data");
            return c;
        } catch (Throwable t) {
            logFallback("deserialize", t);
            return fallbackFromJson(json, lenient);
        }
    }

    private static String fallbackToJson(Component component) {
        return FALLBACK_GSON.toJson(Component.Serializer.toJsonTree(component));
    }

    private static MutableComponent fallbackFromJson(String json, boolean lenient) {
        JsonReader r = new JsonReader(new StringReader(json));
        r.setLenient(true);
        JsonElement tree = Streams.parse(r);
        if (tree == null || tree.isJsonNull()) return null;
        return Component.Serializer.fromJson(tree);
    }

    private static void logFallback(String op, Throwable t) {
        if (!fallbackLogged) {
            fallbackLogged = true;
            org.slf4j.LoggerFactory.getLogger("AE2IsAllYouNeed").warn("[Core] component json fast path fell back on {}: {}", op, t.toString());
        }
    }

    private static final class FallbackSignal extends RuntimeException {
        FallbackSignal(String msg) {
            super(msg);
        }

        FallbackSignal(Throwable cause) {
            super(cause);
        }
    }

    private static void writeComponent(JsonWriter w, Component c) throws IOException {
        w.beginObject();
        Style style = c.getStyle();
        if (style != null && !style.isEmpty()) writeStyle(w, style);
        List<Component> siblings = c.getSiblings();
        if (!siblings.isEmpty()) {
            w.name("extra");
            w.beginArray();
            for (Component sib : siblings) writeComponent(w, sib);
            w.endArray();
        }
        writeContents(w, c.getContents());
        w.endObject();
    }

    private static void writeStyle(JsonWriter w, Style s) throws IOException {
        int flags = STYLE_ACCESS.flags(s);
        if ((flags & 1) != 0) w.name("bold").value((flags & 2) != 0);
        if ((flags & 4) != 0) w.name("italic").value((flags & 8) != 0);
        if ((flags & 16) != 0) w.name("underlined").value((flags & 32) != 0);
        if ((flags & 64) != 0) w.name("strikethrough").value((flags & 128) != 0);
        if ((flags & 256) != 0) w.name("obfuscated").value((flags & 512) != 0);
        TextColor color = s.getColor();
        if (color != null) {
            String v = color.serialize();
            if (v != null) w.name("color").value(v);
        }
        String insertion = s.getInsertion();
        if (insertion != null) w.name("insertion").value(insertion);
        ClickEvent click = s.getClickEvent();
        if (click != null) {
            w.name("clickEvent");
            w.beginObject();
            w.name("action").value(click.getAction().getName());
            String v = click.getValue();
            if (v != null) w.name("value").value(v);
            w.endObject();
        }
        HoverEvent hover = s.getHoverEvent();
        if (hover != null) {
            w.name("hoverEvent");
            JsonObject tree = hover.serialize();
            FALLBACK_GSON.toJson(tree, w);
        }
        ResourceLocation font = STYLE_ACCESS.rawFont(s);
        if (font != null) w.name("font").value(font.toString());
    }

    private static void writeContents(JsonWriter w, ComponentContents contents) throws IOException {
        if (contents == null || contents == ComponentContents.EMPTY) {
            w.name("text").value("");
        } else if (contents instanceof LiteralContents literal) {
            w.name("text").value(literal.text());
        } else if (contents instanceof TranslatableContents tr) {
            w.name("translate").value(tr.getKey());
            String fallback = tr.getFallback();
            if (fallback != null) w.name("fallback").value(fallback);
            Object[] args = tr.getArgs();
            if (args.length > 0) {
                w.name("with");
                w.beginArray();
                for (Object arg : args) {
                    if (arg instanceof Component ac) {
                        writeComponent(w, ac);
                    } else {
                        w.value(String.valueOf(arg));
                    }
                }
                w.endArray();
            }
        } else if (contents instanceof ScoreContents score) {
            w.name("score");
            w.beginObject();
            w.name("name").value(score.getName());
            w.name("objective").value(score.getObjective());
            w.endObject();
        } else if (contents instanceof SelectorContents sel) {
            w.name("selector").value(sel.getPattern());
            Optional<Component> sep = sel.getSeparator();
            if (sep.isPresent()) {
                w.name("separator");
                writeComponent(w, sep.get());
            }
        } else if (contents instanceof KeybindContents keybind) {
            w.name("keybind").value(keybind.getName());
        } else if (contents instanceof NbtContents nbt) {
            w.name("nbt").value(nbt.getNbtPath());
            w.name("interpret").value(nbt.isInterpreting());
            Optional<Component> sep = nbt.getSeparator();
            if (sep.isPresent()) {
                w.name("separator");
                writeComponent(w, sep.get());
            }
            DataSource source = nbt.getDataSource();
            if (source instanceof BlockDataSource block) {
                w.name("block").value(block.posPattern());
            } else if (source instanceof EntityDataSource entity) {
                w.name("entity").value(entity.selectorPattern());
            } else if (source instanceof StorageDataSource storage) {
                w.name("storage").value(storage.id().toString());
            } else {
                throw new FallbackSignal("unknown nbt data source " + source);
            }
        } else {
            throw new FallbackSignal("unknown contents " + contents.getClass().getName());
        }
    }

    private static MutableComponent readComponent(JsonReader r) throws IOException {
        return switch (r.peek()) {
            case STRING, NUMBER -> Component.literal(r.nextString());
            case BOOLEAN -> Component.literal(Boolean.toString(r.nextBoolean()));
            case NULL -> {
                r.nextNull();
                yield null;
            }
            case BEGIN_ARRAY -> {
                r.beginArray();
                if (!r.hasNext()) {
                    r.endArray();
                    throw new JsonParseException("Unexpected empty array of components");
                }
                MutableComponent first = readComponent(r);
                while (r.hasNext()) {
                    MutableComponent next = readComponent(r);
                    if (first == null) first = next;
                    else first.append(next);
                }
                r.endArray();
                yield first;
            }
            case BEGIN_OBJECT -> readComponentObject(r);
            default -> {
                r.skipValue();
                throw new JsonParseException("Don't know how to turn token into a Component");
            }
        };
    }

    private static MutableComponent readComponentObject(JsonReader r) throws IOException {
        String text = null;
        String translate = null, fallback = null;
        List<Object> with = null;
        String scoreName = null, scoreObjective = null;
        boolean hasScore = false;
        String selector = null;
        Component separator = null;
        String keybind = null;
        String nbtPath = null;
        boolean interpret = false;
        String nbtBlock = null, nbtEntity = null, nbtStorage = null;
        boolean hasNbt = false;
        List<Component> extra = null;
        TextColor color = null;
        int flagMask = 0;
        String insertion = null;
        ClickEvent clickEvent = null;
        HoverEvent hoverEvent = null;
        ResourceLocation font = null;

        r.beginObject();
        while (r.hasNext()) {
            String name = r.nextName();
            switch (name) {
                case "text" -> text = nextStringValue(r);
                case "translate" -> translate = nextStringValue(r);
                case "fallback" -> fallback = nextStringValue(r);
                case "with" -> {
                    r.beginArray();
                    with = new ArrayList<>();
                    while (r.hasNext()) with.add(readWithArg(r));
                    r.endArray();
                }
                case "score" -> {
                    hasScore = true;
                    r.beginObject();
                    while (r.hasNext()) {
                        String k = r.nextName();
                        if (k.equals("name")) scoreName = nextStringValue(r);
                        else if (k.equals("objective")) scoreObjective = nextStringValue(r);
                        else r.skipValue();
                    }
                    r.endObject();
                }
                case "selector" -> selector = nextStringValue(r);
                case "separator" -> separator = readComponent(r);
                case "keybind" -> keybind = nextStringValue(r);
                case "nbt" -> {
                    nbtPath = nextStringValue(r);
                    hasNbt = true;
                }
                case "interpret" -> interpret = nextBooleanValue(r);
                case "block" -> nbtBlock = nextStringValue(r);
                case "entity" -> nbtEntity = nextStringValue(r);
                case "storage" -> nbtStorage = nextStringValue(r);
                case "extra" -> {
                    r.beginArray();
                    extra = new ArrayList<>();
                    while (r.hasNext()) {
                        MutableComponent c = readComponent(r);
                        if (c == null) throw new JsonParseException("null component in extra");
                        extra.add(c);
                    }
                    r.endArray();
                    if (extra.isEmpty()) throw new JsonParseException("Unexpected empty array of components");
                }
                case "bold" -> flagMask = flag(flagMask, 0, nextOptionalFlag(r));
                case "italic" -> flagMask = flag(flagMask, 1, nextOptionalFlag(r));
                case "underlined" -> flagMask = flag(flagMask, 2, nextOptionalFlag(r));
                case "strikethrough" -> flagMask = flag(flagMask, 3, nextOptionalFlag(r));
                case "obfuscated" -> flagMask = flag(flagMask, 4, nextOptionalFlag(r));
                case "color" -> color = TextColor.parseColor(nextStringValue(r));
                case "insertion" -> insertion = nextStringValue(r);
                case "clickEvent" -> clickEvent = readClickEvent(r);
                case "hoverEvent" -> hoverEvent = readHoverEvent(r);
                case "font" -> font = new ResourceLocation(nextStringValue(r));
                default -> r.skipValue();
            }
        }
        r.endObject();

        MutableComponent result;
        if (text != null) {
            result = Component.literal(text);
        } else if (translate != null) {
            result = with != null
                    ? Component.translatableWithFallback(translate, fallback, with.toArray())
                    : Component.translatableWithFallback(translate, fallback);
        } else if (hasScore) {
            if (scoreName == null || scoreObjective == null)
                throw new JsonParseException("A score component needs a least a name and an objective");
            result = Component.score(scoreName, scoreObjective);
        } else if (selector != null) {
            result = Component.selector(selector, Optional.ofNullable(separator));
        } else if (keybind != null) {
            result = Component.keybind(keybind);
        } else if (hasNbt) {
            DataSource source;
            if (nbtBlock != null) source = new BlockDataSource(nbtBlock);
            else if (nbtEntity != null) source = new EntityDataSource(nbtEntity);
            else if (nbtStorage != null) source = new StorageDataSource(new ResourceLocation(nbtStorage));
            else throw new JsonParseException("A nbt component needs a data source");
            result = Component.nbt(nbtPath, interpret, Optional.ofNullable(separator), source);
        } else {
            throw new JsonParseException("Don't know how to turn object into a Component");
        }

        if (extra != null) for (Component c : extra) result.append(c);

        result.setStyle(STYLE_ACCESS.build(color, flagMask, clickEvent, hoverEvent, insertion, font));
        return result;
    }

    private static int flag(int mask, int idx, Boolean v) {
        if (v == null) return mask;
        return mask | (1 << (idx * 2)) | (v ? (2 << (idx * 2)) : 0);
    }

    private static Object readWithArg(JsonReader r) throws IOException {
        MutableComponent c = readComponent(r);
        if (c == null) return null;
        if (c.getStyle().isEmpty() && c.getSiblings().isEmpty() && c.getContents() instanceof LiteralContents literal) {
            return literal.text();
        }
        return c;
    }

    private static String nextStringValue(JsonReader r) throws IOException {
        return switch (r.peek()) {
            case STRING, NUMBER -> r.nextString();
            case BOOLEAN -> Boolean.toString(r.nextBoolean());
            default -> {
                r.skipValue();
                throw new JsonParseException("expected string value");
            }
        };
    }

    private static boolean nextBooleanValue(JsonReader r) throws IOException {
        return switch (r.peek()) {
            case BOOLEAN -> r.nextBoolean();
            case STRING, NUMBER -> Boolean.parseBoolean(r.nextString());
            default -> {
                r.skipValue();
                throw new JsonParseException("expected boolean value");
            }
        };
    }

    private static Boolean nextOptionalFlag(JsonReader r) throws IOException {
        return switch (r.peek()) {
            case BOOLEAN -> r.nextBoolean();
            case STRING, NUMBER -> Boolean.valueOf(r.nextString());
            default -> {
                r.skipValue();
                throw new JsonParseException("expected boolean flag");
            }
        };
    }

    private static ClickEvent readClickEvent(JsonReader r) throws IOException {
        if (r.peek() != JsonToken.BEGIN_OBJECT) {
            r.skipValue();
            throw new JsonParseException("expected clickEvent object");
        }
        String action = null, value = null;
        r.beginObject();
        while (r.hasNext()) {
            String k = r.nextName();
            if (k.equals("action")) action = nextStringValue(r);
            else if (k.equals("value")) value = nextStringValue(r);
            else r.skipValue();
        }
        r.endObject();
        if (action == null || value == null) return null;
        ClickEvent.Action a = ClickEvent.Action.getByName(action);
        if (a == null || !a.isAllowedFromServer()) return null;
        return new ClickEvent(a, value);
    }

    private static HoverEvent readHoverEvent(JsonReader r) throws IOException {
        JsonElement tree = Streams.parse(r);
        if (tree == null || !tree.isJsonObject()) throw new JsonParseException("expected hoverEvent object");
        return HoverEvent.deserialize(tree.getAsJsonObject());
    }

    private abstract static class StyleAccess {
        abstract int flags(Style s);

        abstract ResourceLocation rawFont(Style s);

        abstract Style build(TextColor color, int flagMask, ClickEvent click, HoverEvent hover, String insertion, ResourceLocation font);

        static StyleAccess resolve() {
            try {
                Class<?> ser = Class.forName("net.minecraft.network.chat.Style$Serializer");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                MethodHandle flags = lookup.findStatic(ser, "allyouneed$rawStyleFlags", MethodType.methodType(int.class, Style.class));
                MethodHandle font = lookup.findStatic(ser, "allyouneed$rawFont", MethodType.methodType(ResourceLocation.class, Style.class));
                MethodHandle build = lookup.findStatic(ser, "allyouneed$buildStyle", MethodType.methodType(Style.class,
                        TextColor.class, int.class, ClickEvent.class, HoverEvent.class, String.class, ResourceLocation.class));
                return new Injected(flags, font, build);
            } catch (Throwable ignored) {
            }
            try {
                return new Reflective();
            } catch (Throwable t) {
                return null;
            }
        }

        static Boolean flagAt(int mask, int idx) {
            return (mask & (1 << (idx * 2))) != 0 ? (mask & (2 << (idx * 2))) != 0 : null;
        }

        static final class Injected extends StyleAccess {
            private final MethodHandle flags, font, build;

            Injected(MethodHandle flags, MethodHandle font, MethodHandle build) {
                this.flags = flags;
                this.font = font;
                this.build = build;
            }

            @Override
            int flags(Style s) {
                try {
                    return (int) flags.invokeExact(s);
                } catch (Throwable t) {
                    throw new FallbackSignal(t);
                }
            }

            @Override
            ResourceLocation rawFont(Style s) {
                try {
                    return (ResourceLocation) font.invokeExact(s);
                } catch (Throwable t) {
                    throw new FallbackSignal(t);
                }
            }

            @Override
            Style build(TextColor color, int flagMask, ClickEvent click, HoverEvent hover, String insertion, ResourceLocation font) {
                try {
                    return (Style) build.invokeExact(color, flagMask, click, hover, insertion, font);
                } catch (Throwable t) {
                    throw new FallbackSignal(t);
                }
            }
        }

        static final class Reflective extends StyleAccess {
            private final Field bold, italic, underlined, strikethrough, obfuscated, font;
            private final Constructor<Style> ctor;

            Reflective() throws Exception {
                bold = field("bold");
                italic = field("italic");
                underlined = field("underlined");
                strikethrough = field("strikethrough");
                obfuscated = field("obfuscated");
                font = field("font");
                ctor = Style.class.getDeclaredConstructor(TextColor.class, Boolean.class, Boolean.class, Boolean.class,
                        Boolean.class, Boolean.class, ClickEvent.class, HoverEvent.class, String.class, ResourceLocation.class);
                ctor.setAccessible(true);
            }

            private static Field field(String name) throws Exception {
                Field f = Style.class.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            }

            @Override
            int flags(Style s) {
                try {
                    int m = 0;
                    Boolean b = (Boolean) bold.get(s);
                    if (b != null) m |= b ? 3 : 1;
                    b = (Boolean) italic.get(s);
                    if (b != null) m |= b ? 12 : 4;
                    b = (Boolean) underlined.get(s);
                    if (b != null) m |= b ? 48 : 16;
                    b = (Boolean) strikethrough.get(s);
                    if (b != null) m |= b ? 192 : 64;
                    b = (Boolean) obfuscated.get(s);
                    if (b != null) m |= b ? 768 : 256;
                    return m;
                } catch (Throwable t) {
                    throw new FallbackSignal(t);
                }
            }

            @Override
            ResourceLocation rawFont(Style s) {
                try {
                    return (ResourceLocation) font.get(s);
                } catch (Throwable t) {
                    throw new FallbackSignal(t);
                }
            }

            @Override
            Style build(TextColor color, int flagMask, ClickEvent click, HoverEvent hover, String insertion, ResourceLocation font) {
                try {
                    return ctor.newInstance(color, flagAt(flagMask, 0), flagAt(flagMask, 1), flagAt(flagMask, 2),
                            flagAt(flagMask, 3), flagAt(flagMask, 4), click, hover, insertion, font);
                } catch (Throwable t) {
                    throw new FallbackSignal(t);
                }
            }
        }
    }
}
