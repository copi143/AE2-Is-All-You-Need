package perf;

import allyouneed.gsonfast.ComponentJsonFast;
import net.minecraft.network.chat.Component;

public final class ComponentWorkload implements JsonWorkload {
    private final boolean fast;
    private final String json;
    private final Component component;

    public ComponentWorkload(String mode, String shape) throws Exception {
        fast = mode.equals("fast");
        json = switch (shape) {
            case "plain" -> "{\"text\":\"hello 中文\"}";
            case "styled" -> "{\"text\":\"hello\",\"bold\":false,\"italic\":true,\"color\":\"red\",\"font\":\"minecraft:uniform\",\"extra\":[{\"text\":\"!\"}]}";
            case "translated" -> "{\"translate\":\"test.key\",\"with\":[\"first\",{\"text\":\"second\",\"bold\":true}]}";
            case "hover" -> "{\"text\":\"hover\",\"hoverEvent\":{\"action\":\"show_text\",\"contents\":{\"text\":\"tooltip\",\"italic\":true}}}";
            case "fallback" -> "{\"text\":\"x\",\"bold\":true,\"bold\":false}";
            default -> throw new IllegalArgumentException(shape);
        };
        component = Component.Serializer.fromJson(json);
        if (fast) {
            var field = ComponentJsonFast.class.getDeclaredField("STYLE_ACCESS");
            field.setAccessible(true);
            Object access = field.get(null);
            if (access == null || !access.getClass().getSimpleName().equals("Injected")) {
                throw new IllegalStateException("Benchmark requires the real injected Style accessors");
            }
            String expected = Component.Serializer.toJson(component);
            if (!expected.equals(ComponentJsonFast.toJson(component)) ||
                    !expected.equals(Component.Serializer.toJson(ComponentJsonFast.fromJson(json, false)))) {
                throw new IllegalStateException("Benchmark parity failure: " + shape);
            }
        }
    }

    public String write() { return fast ? ComponentJsonFast.toJson(component) : Component.Serializer.toJson(component); }
    public Object read() { return fast ? ComponentJsonFast.fromJson(json, false) : Component.Serializer.fromJson(json); }
}
