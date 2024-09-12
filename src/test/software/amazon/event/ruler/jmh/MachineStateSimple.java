package software.amazon.event.ruler.jmh;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class MachineStateSimple extends MachineState {
    @Param({
            BENCHMARK_EXACT,
            BENCHMARK_WILDCARD,
            BENCHMARK_PREFIX,
            BENCHMARK_PREFIX_EQUALS_IGNORE_CASE_RULES,
            BENCHMARK_SUFFIX,
            BENCHMARK_SUFFIX_EQUALS_IGNORE_CASE_RULES,
            BENCHMARK_EQUALS_IGNORE_CASE,
            BENCHMARK_NUMERIC,
            BENCHMARK_ANYTHING_BUT,
            BENCHMARK_ANYTHING_BUT_IGNORE_CASE,
            BENCHMARK_ANYTHING_BUT_PREFIX,
            BENCHMARK_ANYTHING_BUT_SUFFIX,
            BENCHMARK_ANYTHING_BUT_WILDCARD,
    })
    public String benchmark;

    @Override
    protected String getBenchmark() {
        return benchmark;
    }
}
