package software.amazon.event.ruler.jmh;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class MachineStateComplex extends MachineState {
    @Param({
            BENCHMARK_COMPLEX_ARRAYS,
            BENCHMARK_PARTIAL_COMBO,
            BENCHMARK_COMBO,
    })
    public String benchmark;

    @Override
    protected String getBenchmark() {
        return benchmark;
    }
}
