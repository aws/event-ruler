package benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import com.fasterxml.jackson.databind.ObjectMapper;

import benchmark.Benchmarker.BenchmarkerBase;
@State(Scope.Thread)
public class DeepEventBenchmark {

    int counter = 0;

    @State(Scope.Benchmark)
    public static class DeepEventsBenchmarker extends BenchmarkerBase {

        // how many levels deep we want to go
        final int maxLevel = 100;

        // we create a rule every time the number of events is a multiple of this number
        final int ruleEveryNEvents = 10;

        ObjectMapper m = new ObjectMapper();

        Map<String, Object> root = new HashMap<>();

        Map<String, Object> ruleRoot = new HashMap<>();

        List<String> deepEvents = new ArrayList<>();
        List<String> deepRules = new ArrayList<>();
        List<Integer> deepExpected = new ArrayList<>();

        @Setup(Level.Trial)
        public void setup() throws Exception {
            Map<String, Object> currentLevel = root;
            Map<String, Object> currentRule = ruleRoot;

            for (int i = 0; i < maxLevel; i++) {
                currentLevel.put("numeric" + i, i * i);
                currentLevel.put("string" + i, "value" + i);

                if (i % ruleEveryNEvents == 0) {
                    currentRule.put("string" + i, Collections.singletonList("value" + i));
                    deepRules.add(m.writeValueAsString(ruleRoot));
                    currentRule.remove("string" + i);
                    // all the events generated below this point will match this rule.
                    deepExpected.add(maxLevel - i);
                }

                deepEvents.add(m.writeValueAsString(root));

                HashMap<String, Object> newLevel = new HashMap<>();
                currentLevel.put("level" + i, newLevel);
                currentLevel = newLevel;

                HashMap<String, Object> newRuleLevel = new HashMap<>();
                currentRule.put("level" + i, newRuleLevel);
                currentRule = newRuleLevel;

            }
            addRules(deepRules.toArray(new String[0]), deepExpected.stream().mapToInt(Integer::intValue).toArray());

        }

        public String getNextEvent(int i) {
            return deepEvents.get(i);
        }

        public int numberOfEvents() {
            return deepEvents.size();
        }
    }

    @Benchmark
    public void deepEventBenchmark(DeepEventsBenchmarker deb, Blackhole bh) throws Exception {
        if (counter >= deb.numberOfEvents()) {
            counter = 0;
        }
        bh.consume(deb.machine.rulesForJSONEvent(deb.getNextEvent(counter)));
        counter++;
    }
}
