package benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.infra.Blackhole;

import software.amazon.event.ruler.Machine;
import software.amazon.event.ruler.RuleCompiler;
import software.amazon.event.ruler.Ruler;

import java.util.zip.GZIPInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static benchmark.Benchmarker.BenchmarkerBase;

@State(Scope.Thread)
public class CityLots2Benchmark {

    private int counter = 0;
    private final int numberOfEvents = 213068;

    @Benchmark
    public void AnythingButPerformanceBenchmark(AnythingButState abs, CityLots2State cl2s, Blackhole bh)
            throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(abs.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void CL2NoCompileBenchmark(CityLots2State cl2s, CityLots2BenchmarkState cl2bs, Blackhole bh)
            throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        for (String rule : cl2bs.rules) {
            bh.consume(Ruler.matchesRule(cl2s.getNextLot(counter), cl2bs.ruleJSON.get(rule)));
        }
        counter++;
    }

    @Benchmark
    public void ExactEventsBenchmark(CityLots2State cl2s, ExactRulesBenchmarker erb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(erb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void WildcardEventsBenchmark(CityLots2State cl2s, WildcardRulesBenchmarker wrb, Blackhole bh)
            throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(wrb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void PrefixRulesBenchmark(CityLots2State cl2s, PrefixRulesBenchmarker prb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(prb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void SuffixRulesBenchmark(CityLots2State cl2s, SuffixRulesBenchmarker srb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(srb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void EqualsIgnoreCaseRulesBenchmark(CityLots2State cl2s, EqualsIgnoreCaseRulesBenchmarker eicrb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(eicrb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void NumericRulesBenchmark(CityLots2State cl2s, NumericRulesBenchmarker nrb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(nrb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void AnythingButRulesBenchmark(CityLots2State cl2s, AnythingButBenchmarker abb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(abb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void AnythingButIgnoreCaseRulesBenchmark(CityLots2State cl2s, AnythingButIgnoreCaseBenchmarker abicb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(abicb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void AnythingButPrefixRulesBenchmark(CityLots2State cl2s, AnythingButPrefixBenchmarker abpb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(abpb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void AnythingButSuffixRulesBenchmark(CityLots2State cl2s, AnythingButSuffixBenchmarker absb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(absb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void ComplexArraysRulesBenchmark(CityLots2State cl2s, ComplexArraysBenchmarker cab, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(cab.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void PartialComboRulesBenchmark(CityLots2State cl2s, PartialComboBenchmarker pcb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(pcb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @Benchmark
    public void ComboRulesBenchmark(CityLots2State cl2s, ComboBenchmarker cb, Blackhole bh) throws Exception {
        if (counter >= numberOfEvents) {
            counter = 0;
        }
        bh.consume(cb.machine.rulesForJSONEvent(cl2s.getNextLot(counter)));
        counter++;
    }

    @State(Scope.Benchmark)
    public static class CityLots2State {
        @Setup(Level.Trial)
        public void readCityLots2() {
            try {
                System.out.println("Reading citylots2");
                final FileInputStream fileInputStream = new FileInputStream(CITYLOTS_2);
                final GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
                BufferedReader cl2Reader = new BufferedReader(new InputStreamReader(gzipInputStream));
                String line = cl2Reader.readLine();
                while (line != null) {
                    citylots2.add(line);
                    line = cl2Reader.readLine();
                }
                cl2Reader.close();
                System.out.println("Read " + citylots2.size() + " events");
                // Collections.shuffle(citylots2);

            } catch (Exception e) {
                System.out.println("Can't find, current directory " + System.getProperty("user.dir"));
                throw new RuntimeException(e);
            }
        }

        public String getNextLot(int counter) {
            return citylots2.get(counter);
        }

        public final List<String> citylots2 = new ArrayList<>();

        private static final String CITYLOTS_2 = "src/main/java/benchmark/data/citylots2.json.gz";
    }

    @State(Scope.Benchmark)
    public static class AnythingButState {

        @Setup(Level.Iteration)
        public void setupMachine() throws Exception {
            machine = new Machine();
            machine.addRule("r", anythingButRule);
        }

        public Machine machine;

        private final String anythingButRule = "{\n" +
                "  \"properties\": {\n" +
                "    \"ODD_EVEN\": [ {\"anything-but\":\"E\"} ],\n" +
                "    \"ST_TYPE\": [ {\"anything-but\":\"ST\"} ]\n" +
                "  },\n" +
                "  \"geometry\": {\n" +
                "    \"coordinates\": {\n" +
                "      \"z\": [ {\"anything-but\": 1} ] \n" +
                "    }\n" +
                "  }\n" +
                "}";

    }

    @State(Scope.Benchmark)
    public static class CityLots2BenchmarkState {

        public Set<String> rules = new HashSet<>();
        public Map<String, String> ruleJSON = new HashMap<>();

        @Setup(Level.Iteration)
        public void setRules() {

            List<Rule> tempRules = new ArrayList<>();

            Rule rule;

            rule = new Rule("R1");
            rule.setKeys("properties.STREET", "properties.FROM_ST");
            rule.setJson("{ \"properties\": { \"STREET\": [ \"FOERSTER\" ], \"FROM_ST\": [ \"755\" ] } }");
            rule.setValues("\"FOERSTER\"", "\"755\"");
            tempRules.add(rule);
            ruleJSON.put("R1", rule.json);

            rule = new Rule("R2");
            rule.setJson("{ \"properties\": { \"BLOCK_NUM\": [ \"3027A\" ] } }");
            rule.setKeys("properties.BLOCK_NUM");
            rule.setValues("\"3027A\"");
            tempRules.add(rule);
            ruleJSON.put("R2", rule.json);

            rule = new Rule("R3");
            rule.setKeys("properties.BLOCK_NUM", "properties.ODD_EVEN");
            rule.setValues("\"2183\"", "\"E\"");
            rule.setJson("{ \"properties\": { \"BLOCK_NUM\": [ \"2183\" ], \"ODD_EVEN\": [ \"E\" ] } }");
            tempRules.add(rule);
            ruleJSON.put("R3", rule.json);

            rule = new Rule("R4");
            rule.setKeys("properties.STREET");
            rule.setValues("\"17TH\"");
            rule.setJson("{ \"properties\": { \"STREET\": [ \"17TH\" ] } }");
            tempRules.add(rule);
            ruleJSON.put("R4", rule.json);

            rule = new Rule("R5");
            rule.setKeys("properties.FROM_ST", "geometry.type");
            rule.setValues("\"2521\"", "\"Polygon\"");
            rule.setJson(
                    "{ \"properties\": { \"FROM_ST\": [ \"2521\" ] }, \"geometry\": { \"type\": [ \"Polygon\" ] } }");
            tempRules.add(rule);
            ruleJSON.put("R5", rule.json);

            rule = new Rule("R6");
            rule.setKeys("properties.STREET");
            rule.setValues("\"FOLSOM\"");
            rule.setJson("{ \"properties\": { \"STREET\": [ \"FOLSOM\" ] } }");
            tempRules.add(rule);
            ruleJSON.put("R6", rule.json);

            rule = new Rule("R7");
            rule.setKeys("properties.BLOCK_NUM", "properties.ST_TYPE");
            rule.setValues("\"3789\"", "\"ST\"");
            rule.setJson("{ \"properties\": { \"BLOCK_NUM\": [ \"3789\" ], \"ST_TYPE\": [ \"ST\" ] } }");
            tempRules.add(rule);
            ruleJSON.put("R7", rule.json);

            for (Rule r : tempRules) {
                rules.add(r.name);
            }
        }

    }

    private static class Rule {

        String name;
        String json;
        final Map<String, List<String>> fields = new HashMap<>();
        private String[] keys;

        Rule(String name) {
            this.name = name;
        }

        void setJson(String json) {
            assert RuleCompiler.check(json) == null;
            this.json = json;
        }

        void setKeys(String... keys) {
            this.keys = keys;
        }

        void setValues(String... values) {
            for (int i = 0; i < values.length; i++) {
                final List<String> valList = new ArrayList<>();
                valList.add(values[i]);
                fields.put(keys[i], valList);
            }
        }
    }

    @State(Scope.Benchmark)
    public static class ExactRulesBenchmarker extends BenchmarkerBase {

        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(EXACT_RULES, EXACT_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class WildcardRulesBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(WILDCARD_RULES, WILDCARD_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class PrefixRulesBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(PREFIX_RULES, PREFIX_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class SuffixRulesBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(SUFFIX_RULES, SUFFIX_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class EqualsIgnoreCaseRulesBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(EQUALS_IGNORE_CASE_RULES, EQUALS_IGNORE_CASE_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class NumericRulesBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(NUMERIC_RULES, NUMERIC_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class AnythingButBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(ANYTHING_BUT_RULES, ANYTHING_BUT_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class AnythingButIgnoreCaseBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(ANYTHING_BUT_IGNORE_CASE_RULES, ANYTHING_BUT_IGNORE_CASE_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class AnythingButPrefixBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(ANYTHING_BUT_PREFIX_RULES, ANYTHING_BUT_PREFIX_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class AnythingButSuffixBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(ANYTHING_BUT_SUFFIX_RULES, ANYTHING_BUT_SUFFIX_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class ComplexArraysBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(COMPLEX_ARRAYS_RULES, COMPLEX_ARRAYS_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class PartialComboBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(NUMERIC_RULES, NUMERIC_MATCHES);
            addRules(EXACT_RULES, EXACT_MATCHES);
            addRules(PREFIX_RULES, PREFIX_MATCHES);
            addRules(ANYTHING_BUT_RULES, ANYTHING_BUT_MATCHES);
            addRules(ANYTHING_BUT_IGNORE_CASE_RULES, ANYTHING_BUT_IGNORE_CASE_MATCHES);
            addRules(ANYTHING_BUT_PREFIX_RULES, ANYTHING_BUT_PREFIX_MATCHES);
            addRules(ANYTHING_BUT_SUFFIX_RULES, ANYTHING_BUT_SUFFIX_MATCHES);
        }
    }

    @State(Scope.Benchmark)
    public static class ComboBenchmarker extends BenchmarkerBase {
        @Setup(Level.Trial)
        public void AddRules() throws Exception {
            addRules(NUMERIC_RULES, NUMERIC_MATCHES);
            addRules(EXACT_RULES, EXACT_MATCHES);
            addRules(PREFIX_RULES, PREFIX_MATCHES);
            addRules(ANYTHING_BUT_RULES, ANYTHING_BUT_MATCHES);
            addRules(ANYTHING_BUT_IGNORE_CASE_RULES, ANYTHING_BUT_IGNORE_CASE_MATCHES);
            addRules(ANYTHING_BUT_PREFIX_RULES, ANYTHING_BUT_PREFIX_MATCHES);
            addRules(ANYTHING_BUT_SUFFIX_RULES, ANYTHING_BUT_SUFFIX_MATCHES);
            addRules(COMPLEX_ARRAYS_RULES, COMPLEX_ARRAYS_MATCHES);
        }
    }
}