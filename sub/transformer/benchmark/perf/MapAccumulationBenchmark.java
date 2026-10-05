package perf;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms256m", "-Xmx256m"})
public class MapAccumulationBenchmark {
    @Param({"stock", "asm"}) public String mode;
    @Param({"identity", "string", "custom"}) public String keys;
    @Param({"256", "4096"}) public int distinct;
    private AccumulationWorkload workload;

    @Setup
    public void setup() throws Exception {
        System.setProperty("allyouneed.mapaccumulation", "true");
        workload = (AccumulationWorkload) new BenchmarkLoader(false, false, mode.equals("asm"))
                .loadClass("perf.MapAccumulationWorkload").getConstructor(String.class, int.class).newInstance(keys, distinct);
        if (workload.checksum(workload.count()) != 16384) throw new IllegalStateException("Incorrect accumulated counts");
    }

    @Benchmark
    @OperationsPerInvocation(16384)
    public Object count() { return workload.count(); }
}
