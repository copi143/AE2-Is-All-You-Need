package perf;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms256m", "-Xmx256m"})
public class ComponentJsonBenchmark {
    @Param({"stock", "fast"}) public String mode;
    @Param({"plain", "styled", "translated", "hover", "fallback"}) public String shape;
    private JsonWorkload workload;
    @Setup public void setup() throws Exception {
        workload = (JsonWorkload) new BenchmarkLoader(false, true).loadClass("perf.ComponentWorkload")
                .getConstructor(String.class, String.class).newInstance(mode, shape);
    }
    @Benchmark public String write() { return workload.write(); }
    @Benchmark public Object read() { return workload.read(); }
}
