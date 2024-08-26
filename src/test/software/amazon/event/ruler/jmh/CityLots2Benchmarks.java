package software.amazon.event.ruler.jmh;

import static java.util.concurrent.TimeUnit.SECONDS;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;


@BenchmarkMode(Mode.Throughput)
@Fork(value = 5, jvmArgsAppend = {"-Xmx2g", "-Xms2g", "-XX:+AlwaysPreTouch"})
@Timeout(time = 90, timeUnit = SECONDS)
public class CityLots2Benchmarks {

    @Benchmark
    @Warmup(iterations = 6, batchSize = 1, time = 10, timeUnit = SECONDS)
    @Measurement(iterations = 6, batchSize = 1, time = 10, timeUnit = SECONDS)
    public void run01Simple(MachineStateSimple machineState, CityLots2State cityLots2State,
                            EventsCounter eventsCounter, Blackhole blackhole) throws Exception {
        run(machineState, cityLots2State, eventsCounter, blackhole);
    }

    @Benchmark
    @Warmup(iterations = 2, batchSize = 1, time = 60, timeUnit = SECONDS)
    @Measurement(iterations = 5, batchSize = 1, time = 60, timeUnit = SECONDS)
    public void run02Combo(MachineStateCombo machineState, CityLots2State cityLots2State,
                           EventsCounter eventsCounter, Blackhole blackhole) throws Exception {
        run(machineState, cityLots2State, eventsCounter, blackhole);
    }

    private void run(MachineState machineState,
                     CityLots2State cityLots2State,
                     EventsCounter eventsCounter,
                     Blackhole blackhole) throws Exception {
        for (String event : cityLots2State.citylots2) {
            blackhole.consume(machineState.machine.rulesForJSONEvent(event));
        }
        eventsCounter.events += cityLots2State.citylots2.size();
    }
}

