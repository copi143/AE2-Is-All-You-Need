package perf;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms256m", "-Xmx256m"})
public class GsonBindingBenchmark {
    @Param({"stock", "cached", "uncached"}) public String mode;
    private JsonWorkload workload;
    @Setup public void setup() throws Exception { workload = BenchmarkLoader.gson(mode, true); }
    @Benchmark public Object newGsonAndBind() { return workload.bind(); }
    @Benchmark public Object newGson() { return workload.newGson(); }
}
