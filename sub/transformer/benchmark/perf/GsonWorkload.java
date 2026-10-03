package perf;

import com.google.gson.Gson;
import java.util.List;

public final class GsonWorkload implements JsonWorkload {
    public static class Sample {
        public int count = 17;
        public long timestamp = 123456789L;
        public boolean enabled = true;
        public String name = "sample \"text\" 中文";
        public List<String> tags = List.of("one", "two", "three");
        public Nested nested = new Nested();
    }
    public static class Nested { public double amount = 3.25; }

    private Gson gson;
    private Sample sample;
    private String json;

    public GsonWorkload(boolean fast, boolean warm) {
        if (warm) {
            gson = new Gson();
            sample = new Sample();
            json = gson.toJson(sample);
            if (gson.getAdapter(Sample.class).getClass().isHidden() != fast) {
                throw new IllegalStateException("Unexpected adapter implementation");
            }
            if (!json.equals(gson.toJson(gson.fromJson(json, Sample.class)))) {
                throw new IllegalStateException("Benchmark roundtrip mismatch");
            }
        }
    }

    public Object bind() { return new Gson().getAdapter(Sample.class); }
    public Object newGson() { return new Gson(); }
    public String write() { return gson.toJson(sample); }
    public Object read() { return gson.fromJson(json, Sample.class); }
}
