package perf;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

/** Includes first Gson initialization and first type binding in each fresh fork. */
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(value = 8, jvmArgsAppend = {"-Xms256m", "-Xmx256m"})
public class GsonColdBindingBenchmark {
    @Param({"stock", "cached", "uncached"}) public String mode;
    private JsonWorkload workload;
    @Setup public void setup() throws Exception { workload = BenchmarkLoader.gson(mode, false); }
    @Benchmark public Object firstGsonAndBinding() { return workload.bind(); }
}
