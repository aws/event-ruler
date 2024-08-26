package software.amazon.event.ruler.jmh;

import static software.amazon.event.ruler.Benchmarks.ANYTHING_BUT_IGNORE_CASE_RULES;
import static software.amazon.event.ruler.Benchmarks.ANYTHING_BUT_PREFIX_RULES;
import static software.amazon.event.ruler.Benchmarks.ANYTHING_BUT_RULES;
import static software.amazon.event.ruler.Benchmarks.ANYTHING_BUT_SUFFIX_RULES;
import static software.amazon.event.ruler.Benchmarks.ANYTHING_BUT_WILDCARD_RULES;
import static software.amazon.event.ruler.Benchmarks.COMPLEX_ARRAYS_RULES;
import static software.amazon.event.ruler.Benchmarks.EQUALS_IGNORE_CASE_RULES;
import static software.amazon.event.ruler.Benchmarks.EXACT_RULES;
import static software.amazon.event.ruler.Benchmarks.NUMERIC_RULES;
import static software.amazon.event.ruler.Benchmarks.PREFIX_EQUALS_IGNORE_CASE_RULES;
import static software.amazon.event.ruler.Benchmarks.PREFIX_RULES;
import static software.amazon.event.ruler.Benchmarks.SUFFIX_EQUALS_IGNORE_CASE_RULES;
import static software.amazon.event.ruler.Benchmarks.SUFFIX_RULES;
import static software.amazon.event.ruler.Benchmarks.WILDCARD_RULES;
import org.openjdk.jmh.annotations.Setup;
import software.amazon.event.ruler.Machine;

public abstract class MachineState {

    static final String BENCHMARK_EXACT = "EXACT";
    static final String BENCHMARK_WILDCARD = "WILDCARD";
    static final String BENCHMARK_PREFIX = "PREFIX";
    static final String BENCHMARK_PREFIX_EQUALS_IGNORE_CASE_RULES = "PREFIX_EQUALS_IGNORE_CASE_RULES";
    static final String BENCHMARK_SUFFIX = "SUFFIX";
    static final String BENCHMARK_SUFFIX_EQUALS_IGNORE_CASE_RULES = "SUFFIX_EQUALS_IGNORE_CASE_RULES";
    static final String BENCHMARK_EQUALS_IGNORE_CASE = "EQUALS_IGNORE_CASE";
    static final String BENCHMARK_NUMERIC = "NUMERIC";
    static final String BENCHMARK_ANYTHING_BUT = "ANYTHING-BUT";
    static final String BENCHMARK_ANYTHING_BUT_IGNORE_CASE = "ANYTHING-BUT-IGNORE-CASE";
    static final String BENCHMARK_ANYTHING_BUT_PREFIX = "ANYTHING-BUT-PREFIX";
    static final String BENCHMARK_ANYTHING_BUT_SUFFIX = "ANYTHING-BUT-SUFFIX";
    static final String BENCHMARK_ANYTHING_BUT_WILDCARD = "ANYTHING-BUT-WILDCARD";
    static final String BENCHMARK_COMPLEX_ARRAYS = "COMPLEX_ARRAYS";
    static final String BENCHMARK_PARTIAL_COMBO = "PARTIAL_COMBO";
    static final String BENCHMARK_COMBO = "COMBO";

    final Machine machine = new Machine();
    private int ruleCount;

    protected abstract String getBenchmark();

    @Setup
    public void setupRules() throws Exception {
        String benchmark = getBenchmark();

        switch (benchmark) {
            case BENCHMARK_EXACT:
                addRules(EXACT_RULES);
                break;
            case BENCHMARK_WILDCARD:
                addRules(WILDCARD_RULES);
                break;
            case BENCHMARK_PREFIX:
                addRules(PREFIX_RULES);
                break;
            case BENCHMARK_PREFIX_EQUALS_IGNORE_CASE_RULES:
                addRules(PREFIX_EQUALS_IGNORE_CASE_RULES);
                break;
            case BENCHMARK_SUFFIX:
                addRules(SUFFIX_RULES);
                break;
            case BENCHMARK_SUFFIX_EQUALS_IGNORE_CASE_RULES:
                addRules(SUFFIX_EQUALS_IGNORE_CASE_RULES);
                break;
            case BENCHMARK_EQUALS_IGNORE_CASE:
                addRules(EQUALS_IGNORE_CASE_RULES);
                break;
            case BENCHMARK_NUMERIC:
                addRules(NUMERIC_RULES);
                break;
            case BENCHMARK_ANYTHING_BUT:
                addRules(ANYTHING_BUT_RULES);
                break;
            case BENCHMARK_ANYTHING_BUT_IGNORE_CASE:
                addRules(ANYTHING_BUT_IGNORE_CASE_RULES);
                break;
            case BENCHMARK_ANYTHING_BUT_PREFIX:
                addRules(ANYTHING_BUT_PREFIX_RULES);
                break;
            case BENCHMARK_ANYTHING_BUT_SUFFIX:
                addRules(ANYTHING_BUT_SUFFIX_RULES);
                break;
            case BENCHMARK_ANYTHING_BUT_WILDCARD:
                addRules(ANYTHING_BUT_WILDCARD_RULES);
                break;
            case BENCHMARK_COMPLEX_ARRAYS:
                addRules(COMPLEX_ARRAYS_RULES);
                break;
            case BENCHMARK_PARTIAL_COMBO:
                addPartialComboRules();
                break;
            case BENCHMARK_COMBO:
                addPartialComboRules();
                addRules(COMPLEX_ARRAYS_RULES);
                break;
            default:
                throw new IllegalStateException("Unexpected benchmark: " + benchmark);
        }
    }

    private void addPartialComboRules() throws Exception {
        addRules(NUMERIC_RULES);
        addRules(EXACT_RULES);
        addRules(PREFIX_RULES);
        addRules(ANYTHING_BUT_RULES);
        addRules(ANYTHING_BUT_IGNORE_CASE_RULES);
        addRules(ANYTHING_BUT_PREFIX_RULES);
        addRules(ANYTHING_BUT_SUFFIX_RULES);
        addRules(ANYTHING_BUT_WILDCARD_RULES);
    }

    private void addRules(String... rules) throws Exception {
        for (String rule : rules) {
            String rname = String.format("r%d", ruleCount++);
            machine.addRule(rname, rule);
        }
    }
}
