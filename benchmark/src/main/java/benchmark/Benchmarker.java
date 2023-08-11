package benchmark;

import software.amazon.event.ruler.Machine;

public class Benchmarker {
    public static class BenchmarkerBase {
        int ruleCount = 0;
        public Machine machine = new Machine();

        void addRules(String[] rules, int[] wanted) throws Exception {
            for (int i = 0; i < rules.length; i++) {
                String rname = String.format("r%d", ruleCount++);
                machine.addRule(rname, rules[i]);
            }
        }

        public final String[] EXACT_RULES = {
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"MAPBLKLOT\": [ \"1430022\" ]\n" +
                        "  }" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"MAPBLKLOT\": [ \"2607117\" ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"MAPBLKLOT\": [ \"2607218\" ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"MAPBLKLOT\": [ \"3745012\" ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"MAPBLKLOT\": [ \"VACSTWIL\" ]\n" +
                        "  }\n" +
                        "}"
        };

        public final int[] EXACT_MATCHES = { 1, 101, 35, 655, 1 };

        public final String[] WILDCARD_RULES = {
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"MAPBLKLOT\": [ { \"wildcard\": \"143*\" } ]\n" +
                        "  }" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"MAPBLKLOT\": [ { \"wildcard\": \"2*0*1*7\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"MAPBLKLOT\": [ { \"wildcard\": \"*218\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"MAPBLKLOT\": [ { \"wildcard\": \"3*5*2\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"MAPBLKLOT\": [ { \"wildcard\": \"VA*IL\" } ]\n" +
                        "  }\n" +
                        "}"
        };
        public final int[] WILDCARD_MATCHES = { 490, 713, 43, 2540, 1 };

        public final String[] PREFIX_RULES = {
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"prefix\": \"AC\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"prefix\": \"BL\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"prefix\": \"DR\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"prefix\": \"FU\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"prefix\": \"RH\" } ]\n" +
                        "  }\n" +
                        "}"
        };
        public final int[] PREFIX_MATCHES = { 24, 442, 38, 2387, 328 };

        public final String[] SUFFIX_RULES = {
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"suffix\": \"ON\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"suffix\": \"KE\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"suffix\": \"MM\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"suffix\": \"ING\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"suffix\": \"GO\" } ]\n" +
                        "  }\n" +
                        "}"
        };
        public final int[] SUFFIX_MATCHES = { 17921, 871, 13, 1963, 682 };

        public final String[] EQUALS_IGNORE_CASE_RULES = {
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"equals-ignore-case\": \"jefferson\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"equals-ignore-case\": \"bEaCh\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"equals-ignore-case\": \"HyDe\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"equals-ignore-case\": \"CHESTNUT\" } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"ST_TYPE\": [ { \"equals-ignore-case\": \"st\" } ]\n" +
                        "  }\n" +
                        "}"
        };
        public final int[] EQUALS_IGNORE_CASE_MATCHES = { 131, 211, 1758, 825, 116386 };

        public final String[] COMPLEX_ARRAYS_RULES = {
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"type\": [ \"Polygon\" ],\n" +
                        "    \"coordinates\": {\n" +
                        "      \"x\": [ { \"numeric\": [ \"=\", -122.42916360922355 ] } ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"type\": [ \"MultiPolygon\" ],\n" +
                        "    \"coordinates\": {\n" +
                        "      \"y\": [ { \"numeric\": [ \"=\", 37.729900216217324 ] } ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"coordinates\": {\n" +
                        "      \"x\": [ { \"numeric\": [ \"<\", -122.41600944012424 ] } ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"coordinates\": {\n" +
                        "      \"x\": [ { \"numeric\": [ \">\", -122.41600944012424 ] } ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"coordinates\": {\n" +
                        "      \"x\": [ { \"numeric\": [ \">\",  -122.46471267081272, \"<\", -122.4063085128395 ] } ]\n"
                        +
                        "    }\n" +
                        "  }\n" +
                        "}"
        };
        public final int[] COMPLEX_ARRAYS_MATCHES = { 227, 2, 149444, 64368, 127485 };

        public final String[] NUMERIC_RULES = {
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"type\": [ \"Polygon\" ],\n" +
                        "    \"firstCoordinates\": {\n" +
                        "      \"x\": [ { \"numeric\": [ \"=\", -122.42916360922355 ] } ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"type\": [ \"MultiPolygon\" ],\n" +
                        "    \"firstCoordinates\": {\n" +
                        "      \"z\": [ { \"numeric\": [ \"=\", 0 ] } ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"firstCoordinates\": {\n" +
                        "      \"x\": [ { \"numeric\": [ \"<\", -122.41600944012424 ] } ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"firstCoordinates\": {\n" +
                        "      \"x\": [ { \"numeric\": [ \">\", -122.41600944012424 ] } ]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"firstCoordinates\": {\n" +
                        "      \"x\": [ { \"numeric\": [ \">\",  -122.46471267081272, \"<\", -122.4063085128395 ] } ]\n"
                        +
                        "    }\n" +
                        "  }\n" +
                        "}"
        };
        public final int[] NUMERIC_MATCHES = { 8, 120, 148943, 64120, 127053 };

        public final String[] ANYTHING_BUT_RULES = {
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"anything-but\": [ \"FULTON\" ] } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"anything-but\": [ \"MASON\" ] } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"ST_TYPE\": [ { \"anything-but\": [ \"ST\" ] } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"type\": [ {\"anything-but\": [ \"Polygon\" ] } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"FROM_ST\": [ { \"anything-but\": [ \"441\" ] } ]\n" +
                        "  }\n" +
                        "}"
        };
        public final int[] ANYTHING_BUT_MATCHES = { 211158, 210411, 96682, 120, 210615 };

        public final String[] ANYTHING_BUT_IGNORE_CASE_RULES = {
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"anything-but\": {\"equals-ignore-case\": [ \"Fulton\" ] } } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"anything-but\": {\"equals-ignore-case\": [ \"Mason\" ] } } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"ST_TYPE\": [ { \"anything-but\": {\"equals-ignore-case\": [ \"st\" ] } } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"type\": [ {\"anything-but\": {\"equals-ignore-case\": [ \"polygon\" ] } } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"FROM_ST\": [ { \"anything-but\": {\"equals-ignore-case\": [ \"441\" ] } } ]\n" +
                        "  }\n" +
                        "}"
        };
        public final int[] ANYTHING_BUT_IGNORE_CASE_MATCHES = { 211158, 210411, 96682, 120, 210615 };

        public final String[] ANYTHING_BUT_PREFIX_RULES = {
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"anything-but\": {\"prefix\": \"FULTO\" } } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"anything-but\": { \"prefix\": \"MASO\"} } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"ST_TYPE\": [ { \"anything-but\": {\"prefix\": \"S\"}  } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"type\": [ {\"anything-but\": {\"prefix\": \"Poly\"} } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"FROM_ST\": [ { \"anything-but\": {\"prefix\": \"44\"} } ]\n" +
                        "  }\n" +
                        "}"
        };
        public final int[] ANYTHING_BUT_PREFIX_MATCHES = { 211158, 210118, 96667, 120, 209091 };

        public final String[] ANYTHING_BUT_SUFFIX_RULES = {
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"anything-but\": {\"suffix\": \"ULTON\" } } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"STREET\": [ { \"anything-but\": { \"suffix\": \"ASON\"} } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"ST_TYPE\": [ { \"anything-but\": {\"suffix\": \"T\"}  } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"geometry\": {\n" +
                        "    \"type\": [ {\"anything-but\": {\"suffix\": \"olygon\"} } ]\n" +
                        "  }\n" +
                        "}",
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"FROM_ST\": [ { \"anything-but\": {\"suffix\": \"41\"} } ]\n" +
                        "  }\n" +
                        "}"
        };
        public final int[] ANYTHING_BUT_SUFFIX_MATCHES = { 211136, 210411, 94908, 0, 209055 };

    }

}


