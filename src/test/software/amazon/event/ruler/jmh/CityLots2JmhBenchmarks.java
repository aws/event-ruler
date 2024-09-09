package software.amazon.event.ruler.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.event.ruler.Machine;

import java.util.Objects;

import static java.util.concurrent.TimeUnit.SECONDS;


@BenchmarkMode(Mode.Throughput)
@Fork(value = 2, jvmArgsAppend = {
        "-Xmx2g", "-Xms2g", "-XX:+AlwaysPreTouch", "-XX:+UseTransparentHugePages", "-XX:+UseSerialGC",
        "-XX:-BackgroundCompilation", "-XX:CompileCommand=dontinline,com/fasterxml/*.*",
})
@Timeout(time = 90, timeUnit = SECONDS)
@OperationsPerInvocation(CityLots2State.DATASET_SIZE)
public class CityLots2JmhBenchmarks {

    @Benchmark
    @Warmup(iterations = 4, batchSize = 1, time = 10, timeUnit = SECONDS)
    @Measurement(iterations = 5, batchSize = 1, time = 10, timeUnit = SECONDS)
    public void group01Simple(MachineStateSimple machineState, CityLots2State cityLots2State, Blackhole blackhole) throws Exception {
        run(machineState, cityLots2State, blackhole);
    }

    @Benchmark
    @Warmup(iterations = 2, batchSize = 1, time = 60, timeUnit = SECONDS)
    @Measurement(iterations = 3, batchSize = 1, time = 60, timeUnit = SECONDS)
    public void group02Complex(MachineStateComplex machineState, CityLots2State cityLots2State, Blackhole blackhole) throws Exception {
        run(machineState, cityLots2State, blackhole);
    }

    private void run(MachineState machineState, CityLots2State cityLots2State, Blackhole blackhole) throws Exception {
        Machine machine = Objects.requireNonNull(machineState.machine);

        for (String event : cityLots2State.getCityLots2()) {
            blackhole.consume(machine.rulesForJSONEvent(event));
        }
    }
}

