package perf;

import allyouneed.transformer.ComponentJsonTransformer;
import allyouneed.transformer.GsonFastPathTransformer;
import allyouneed.transformer.MapAccumulationTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;

/** Executes the real hooks in isolation, including Gson package access and Style nest access. */
public final class BenchmarkLoader extends ClassLoader {
    private final boolean gsonHook;
    private final boolean styleHook;
    private final boolean mapHook;

    public BenchmarkLoader(boolean gsonHook, boolean styleHook) {
        this(gsonHook, styleHook, false);
    }

    public BenchmarkLoader(boolean gsonHook, boolean styleHook, boolean mapHook) {
        super(BenchmarkLoader.class.getClassLoader());
        this.gsonHook = gsonHook;
        this.styleHook = styleHook;
        this.mapHook = mapHook;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!(name.startsWith("com.google.gson.") || name.startsWith("net.minecraft.")
                || name.startsWith("allyouneed.gsonfast.") || name.startsWith("perf.GsonWorkload")
                || name.startsWith("perf.ComponentWorkload") || name.startsWith("perf.MapAccumulationWorkload"))) return super.loadClass(name, resolve);
        synchronized (getClassLoadingLock(name)) {
            Class<?> cls = findLoadedClass(name);
            if (cls == null) {
                try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (input == null) throw new ClassNotFoundException(name);
                    byte[] bytes = input.readAllBytes();
                    boolean gson = gsonHook && name.equals("com.google.gson.internal.bind.ReflectiveTypeAdapterFactory");
                    boolean style = styleHook && name.equals("net.minecraft.network.chat.Style$Serializer");
                    boolean map = mapHook && name.equals("perf.MapAccumulationWorkload");
                    if (gson || style || map) {
                        ClassNode node = new ClassNode();
                        new ClassReader(bytes).accept(node, 0);
                        boolean changed = gson ? GsonFastPathTransformer.INSTANCE.apply(node)
                                : map ? MapAccumulationTransformer.INSTANCE.apply(node) > 0 : ComponentJsonTransformer.INSTANCE.apply(node);
                        if (!changed) throw new IllegalStateException("Benchmark hook failed: " + name);
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                        node.accept(writer);
                        bytes = writer.toByteArray();
                    }
                    cls = defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
            if (resolve) resolveClass(cls);
            return cls;
        }
    }

    public static JsonWorkload gson(String mode, boolean warm) throws Exception {
        System.setProperty("allyouneed.gsonfast.cache", mode.equals("uncached") ? "false" : "true");
        boolean fast = !mode.equals("stock");
        return (JsonWorkload) new BenchmarkLoader(fast, false).loadClass("perf.GsonWorkload")
                .getConstructor(boolean.class, boolean.class).newInstance(fast, warm);
    }
}
