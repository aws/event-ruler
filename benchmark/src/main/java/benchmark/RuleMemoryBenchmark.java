package benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import software.amazon.event.ruler.Machine;

@State(Scope.Thread)
public class RuleMemoryBenchmark {
    
    @State(Scope.Benchmark)
    public static class Rules {

        public List<String> rules = new ArrayList<>();

        @Setup(Level.Trial)
        public void setup() {
            // 10 random field names
            String[] fieldNames = new String[NUMBER_OF_FIELD_NAMES];
            for (int i = 0; i < NUMBER_OF_FIELD_NAMES; i++) {
                fieldNames[i] = randomAscii(12);
            }

            for (int i = 0; i < NUMBER_OF_RULES; i++) {
                String rule = ruleTemplate;
                rule = rule.replace("-FIELD1", fieldNames[i % NUMBER_OF_FIELD_NAMES]);
                //rule = rule.replace("-NUMBER1", Double.toString(1000000.0 * rand.nextDouble()));
                rule = rule.replace("-NUMBER1", Integer.toString(rand.nextInt(100)));
                rule = rule.replace("-PREFIX", randomAscii(6));
                rules.add(rule);

                // handy when fooling with rule templates
                // if (i < 12) {
                //    System.out.println("R: " + rule);
                // }
            }
        }

        @TearDown(Level.Trial)
        public void clearRules() {
            rules.clear();
        }

        private String randomAscii(final int len) {
            final String abc = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                sb.append(abc.charAt(rand.nextInt(abc.length())));
            }
            return sb.toString();
        }

        private final Random rand = new Random(234345);

        private String ruleTemplate = "{\n" +
                "  \"Abernathy\": {\n" +
                "    \"-FIELD1\": [ -NUMBER1 ]\n" +
                "  },\n" +
                "  \"Barnard\": {\n" +
                "    \"Oliver\": [ { \"prefix\": \"-PREFIX\" } ]\n" +
                "  }\n" +
                "}";
    }

    private final static int NUMBER_OF_RULES = 400000;
    private final static int NUMBER_OF_FIELD_NAMES = 10;

    Machine mm = new Machine();
    int counter = 0;
    
    @Benchmark
    public void ruleMemoryBenchmark(Rules rules) throws Exception {
        if (counter >= rules.rules.size()) {
            counter = 0;
        }
        mm.addRule("mm" + counter, rules.rules.get(counter));
        counter++;
    }

    public static void main(String[] args) throws RunnerException {
            Options opt = new OptionsBuilder()
                    .include(RuleMemoryBenchmark.class.getSimpleName())
                    .addProfiler(StackProfiler.class)
                    .addProfiler(GCProfiler.class)
                    .build();

            new Runner(opt).run();
        }
}
