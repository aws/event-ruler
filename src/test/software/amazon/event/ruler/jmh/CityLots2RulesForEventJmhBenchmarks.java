package software.amazon.event.ruler.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.event.ruler.Machine;

import java.util.List;
import java.util.Objects;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * {@link CityLots2JmhBenchmarks} for {@code rulesForEvent}: the same events, rule sets, threads and JVM flags, but
 * each event is flattened once in setup and matched with {@link Machine#rulesForEvent(List)} instead of
 * {@code rulesForJSONEvent}, so only matching is timed. Iterations are shorter than in
 * {@code CityLots2JmhBenchmarks} because matching a pre-flattened event takes a fraction of the time.
 *
 * <p>Not part of the default {@code run-jmh-benchmarks} execution. Run it with
 * {@code mvn test-compile exec:exec@run-rules-for-event-benchmarks}.</p>
 */
@BenchmarkMode(Mode.Throughput)
@Fork(value = 2, jvmArgsAppend = {
        "-Xmx2g", "-Xms2g", "-XX:+AlwaysPreTouch", "-XX:+UseTransparentHugePages", "-XX:+UseSerialGC",
        "-XX:-BackgroundCompilation", "-XX:CompileCommand=dontinline,com/fasterxml/*.*",
})
@Timeout(time = 90, timeUnit = SECONDS)
@Threads(2)
@OperationsPerInvocation(CityLots2State.DATASET_SIZE)
public class CityLots2RulesForEventJmhBenchmarks {

    @Benchmark
    @Warmup(iterations = 2, batchSize = 1, time = 5, timeUnit = SECONDS)
    @Measurement(iterations = 3, batchSize = 1, time = 5, timeUnit = SECONDS)
    public void group01Simple(MachineStateSimple machineState, FlattenedCityLots2State events, Blackhole blackhole) {
        run(machineState, events, blackhole);
    }

    @Benchmark
    @Warmup(iterations = 2, batchSize = 1, time = 10, timeUnit = SECONDS)
    @Measurement(iterations = 3, batchSize = 1, time = 10, timeUnit = SECONDS)
    public void group02Complex(MachineStateComplex machineState, FlattenedCityLots2State events,
                               Blackhole blackhole) {
        run(machineState, events, blackhole);
    }

    private void run(MachineState machineState, FlattenedCityLots2State events, Blackhole blackhole) {
        Machine machine = Objects.requireNonNull(machineState.machine);
        for (List<String> event : events.getEvents()) {
            blackhole.consume(machine.rulesForEvent(event));
        }
    }
}
