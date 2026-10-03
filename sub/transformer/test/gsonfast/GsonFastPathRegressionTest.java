package gsonfast;

import com.google.gson.*;
import com.google.gson.internal.bind.GsonFastPath;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class GsonFastPathRegressionTest {
    private static class PrivatePojo {
        int n = 7;
        PrivateValue value = new PrivateValue();
    }

    private static class PrivateValue {
        int n = 9;
    }

    public static class StaticPojo {
        public static int n = 7;
    }

    public static class Base { public int base = 1; }
    public static class Sub extends Base { public int sub = 2; }
    public static class Holder { public Base value = new Sub(); }
    public static class CachePojo { public String value = "default"; public int count = 3; }
    public static class CacheGeneric<T> { public T value; }
    public static class ConcurrentPojo { public int value; }
    public static class BoundedPojo { public int value; }

    @Test
    void cachedClassesKeepGsonSpecificAdaptersAndConstructors() {
        GsonBuilder firstBuilder = new GsonBuilder().registerTypeAdapter(String.class,
                (JsonSerializer<String>) (value, type, ctx) -> new JsonPrimitive("first:" + value));
        GsonBuilder secondBuilder = new GsonBuilder().registerTypeAdapter(String.class,
                (JsonSerializer<String>) (value, type, ctx) -> new JsonPrimitive("second:" + value))
                .registerTypeAdapter(CachePojo.class, (InstanceCreator<CachePojo>) type -> {
                    CachePojo pojo = new CachePojo();
                    pojo.value = "created";
                    return pojo;
                });
        Gson first = GsonFastPath.create(firstBuilder), second = GsonFastPath.create(secondBuilder);
        assertSame(first.getAdapter(CachePojo.class).getClass(), second.getAdapter(CachePojo.class).getClass());
        assertEquals(firstBuilder.create().toJson(new CachePojo()), first.toJson(new CachePojo()));
        assertEquals(secondBuilder.create().toJson(new CachePojo()), second.toJson(new CachePojo()));
        assertEquals("default", first.fromJson("{}", CachePojo.class).value);
        assertEquals("created", second.fromJson("{}", CachePojo.class).value);
    }

    @Test
    void namingAndDirectionalExclusionsHaveSeparateLayouts() {
        Gson plain = GsonFastPath.newGson();
        List<GsonBuilder> builders = List.of(
                new GsonBuilder().setFieldNamingStrategy(field -> "prefix_" + field.getName()),
                new GsonBuilder().addSerializationExclusionStrategy(new ExclusionStrategy() {
                    public boolean shouldSkipField(FieldAttributes field) { return field.getName().equals("count"); }
                    public boolean shouldSkipClass(Class<?> type) { return false; }
                }),
                new GsonBuilder().addDeserializationExclusionStrategy(new ExclusionStrategy() {
                    public boolean shouldSkipField(FieldAttributes field) { return field.getName().equals("count"); }
                    public boolean shouldSkipClass(Class<?> type) { return false; }
                }));
        for (GsonBuilder builder : builders) {
            Gson stock = builder.create(), fast = GsonFastPath.create(builder);
            assertNotSame(plain.getAdapter(CachePojo.class).getClass(), fast.getAdapter(CachePojo.class).getClass());
            assertEquals(stock.toJson(new CachePojo()), fast.toJson(new CachePojo()));
            String input = "{\"count\":9,\"value\":\"v\",\"prefix_count\":8,\"prefix_value\":\"p\"}";
            assertEquals(plain.toJson(stock.fromJson(input, CachePojo.class)), plain.toJson(fast.fromJson(input, CachePojo.class)));
        }
    }

    @Test
    void genericArgumentsCanShareCodeWithoutSharingFieldAdapters() {
        Gson gson = GsonFastPath.newGson();
        var strings = com.google.gson.reflect.TypeToken.getParameterized(CacheGeneric.class, String.class);
        var integers = com.google.gson.reflect.TypeToken.getParameterized(CacheGeneric.class, Integer.class);
        assertSame(gson.getAdapter(strings).getClass(), gson.getAdapter(integers).getClass());
        assertInstanceOf(String.class, ((CacheGeneric<?>) gson.fromJson("{\"value\":\"x\"}", strings.getType())).value);
        assertInstanceOf(Integer.class, ((CacheGeneric<?>) gson.fromJson("{\"value\":7}", integers.getType())).value);
    }

    @Test
    void concurrentGsonsReuseOneGeneratedClass() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Class<?>>> requests = new ArrayList<>();
            for (int i = 0; i < 32; i++) requests.add(() -> GsonFastPath.newGson().getAdapter(ConcurrentPojo.class).getClass());
            var results = executor.invokeAll(requests);
            Class<?> first = results.get(0).get();
            assertTrue(first.isHidden());
            for (var result : results) assertSame(first, result.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void arbitraryNamingStrategiesCannotGrowTheCacheWithoutBound() {
        Class<?> first = GsonFastPath.newGson().getAdapter(BoundedPojo.class).getClass();
        for (int i = 0; i < 70; i++) {
            String prefix = "layout" + i;
            Gson gson = GsonFastPath.create(new GsonBuilder().setFieldNamingStrategy(field -> prefix + field.getName()));
            assertTrue(gson.getAdapter(BoundedPojo.class).getClass().isHidden());
        }
        GsonBuilder overflow = new GsonBuilder().setFieldNamingStrategy(field -> "overflow_" + field.getName());
        assertNotSame(GsonFastPath.create(overflow).getAdapter(BoundedPojo.class).getClass(),
                GsonFastPath.create(overflow).getAdapter(BoundedPojo.class).getClass());
        assertSame(first, GsonFastPath.newGson().getAdapter(BoundedPojo.class).getClass());
    }

    @Test
    void privateTypesRoundTrip() {
        Gson fast = GsonFastPath.newGson();
        assertTrue(fast.getAdapter(PrivatePojo.class).getClass().isHidden());
        String input = "{\"n\":3,\"value\":{\"n\":4}}";
        assertEquals(input, fast.toJson(fast.fromJson(input, PrivatePojo.class)));
        assertEquals(new Gson().toJson(new PrivatePojo()), fast.toJson(new PrivatePojo()));
    }

    @Test
    void staticFieldsRoundTrip() {
        GsonBuilder builder = new GsonBuilder().excludeFieldsWithModifiers(Modifier.TRANSIENT);
        Gson fast = GsonFastPath.create(builder);
        int previous = StaticPojo.n;
        try {
            assertTrue(fast.getAdapter(StaticPojo.class).getClass().isHidden());
            assertEquals(builder.create().toJson(new StaticPojo()), fast.toJson(new StaticPojo()));
            fast.fromJson("{\"n\":42}", StaticPojo.class);
            assertEquals(42, StaticPojo.n);
            fast.fromJson("{\"n\":null}", StaticPojo.class);
            assertEquals(42, StaticPojo.n);
        } finally {
            StaticPojo.n = previous;
        }
    }

    @Test
    void declaredCustomSerializerWinsOverReflectiveSubtype() {
        GsonBuilder builder = new GsonBuilder().registerTypeAdapter(Base.class,
                (JsonSerializer<Base>) (value, type, ctx) -> new JsonPrimitive("custom-base"));
        assertEquals(builder.create().toJson(new Holder()), GsonFastPath.create(builder).toJson(new Holder()));
    }

    @Test
    void customSubtypeSerializerStillWins() {
        GsonBuilder builder = new GsonBuilder().registerTypeAdapter(Sub.class,
                (JsonSerializer<Sub>) (value, type, ctx) -> new JsonPrimitive("custom-sub"));
        assertEquals(builder.create().toJson(new Holder()), GsonFastPath.create(builder).toJson(new Holder()));
    }

    @Test
    void childLoaderTypesRoundTrip() throws Exception {
        ClassLoader child = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("gsonfast.pojos.Isolated")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> result = findLoadedClass(name);
                    if (result == null) {
                        try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (stream == null) throw new ClassNotFoundException(name);
                            byte[] bytes = stream.readAllBytes();
                            result = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    }
                    if (resolve) resolveClass(result);
                    return result;
                }
            }
        };
        Class<?> pojo = child.loadClass("gsonfast.pojos.IsolatedPojo");
        Gson stock = new Gson(), fast = GsonFastPath.newGson();
        String json = "{\"a\":3,\"b\":\"x\",\"nested\":{\"v\":4}}";
        assertTrue(fast.getAdapter(pojo).getClass().isHidden());
        assertEquals(stock.toJson(stock.fromJson(json, pojo)), fast.toJson(fast.fromJson(json, pojo)));
    }
}
