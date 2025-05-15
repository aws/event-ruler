package software.amazon.event.ruler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import software.amazon.event.ruler.input.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static software.amazon.event.ruler.input.DefaultParser.getParser;

/**
 * Represents an updated compiler comparing to RuleCompiler class, it parses a rule described by a JSON string into
 * a list of Map which is composed of field Patterns, each Map represents one dedicated match branch in the rule.
 * By default, fields in the rule will be interpreted as "And" relationship implicitly and will result into one Map with
 * fields of Patterns, but once it comes across the "$or" relationship which is explicitly decorated by "$or" primitive
 * array, for each object in "$or" array, this Compiler will fork a new Map to include all fields of patterns in
 * that "or" branch, and when it goes over all branches in "$or" relationship, it will end up with the list of Map
 * and each Map represents a match branch composed of fields of patterns.
 * i.e.: a rule which describe the effect of ("source" AND ("metricName" OR ("metricType AND "namespace") OR "scope"))
 * its JSON format String is:
 *   {
 *     "source": [ "aws.cloudwatch" ],
 *     "$or": [
 *       { "metricName": [ "CPUUtilization", "ReadLatency" ] },
 *       {
 *         "metricType": [ "MetricType" ] ,
 *         "namespace": [ "AWS/EC2", "AWS/ES" ]
 *       },
 *       { "scope": [ "Service" ] }
 *     ]
 *   }
 * It will be parsed to a list of match branches composed by a Map of fields patterns, e.g. for above rule, there will
 * result 3 match branches:
 * [
 *   {source=[\"aws.cloudwatch\"], metricName=[\"CPUUtilization\",\"ReadLatency\"]},
 *   {namespace=[\"AWS/EC2\", \"AWS/ES\"], metricType=[\"MetricType\"], source=[\"aws.cloudwatch\"]},
 *   {source=[\"aws.cloudwatch\"], scope=[\"Service\"]}
 * ]
 */
public class JsonRuleCompiler {

    private static final JsonFactory JSON_FACTORY = JsonFactory.builder()
            .configure(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION, true)
            .build();

    private JsonRuleCompiler() { }

    /**
     * Verify the syntax of a rule
     * @param source rule, as a String
     * @return null if the rule is valid, otherwise an error message
     */
    public static String check(final Reader source, final boolean withOverriding) {
        try {
            doCompile(JSON_FACTORY.createParser(source), withOverriding);
            return null;
        } catch (Exception e) {
            return e.getLocalizedMessage();
        }
    }

    public static String check(final Reader source) {
        return check(source, true);
    }

    public static String check(final String source, final boolean withOverriding) {
        try {
            doCompile(JSON_FACTORY.createParser(source), withOverriding);
            return null;
        } catch (Exception e) {
            return e.getLocalizedMessage();
        }
    }

    public static String check(final String source) {
        return check(source, true);
    }

    public static String check(final byte[] source, final boolean withOverriding) {
        try {
            doCompile(JSON_FACTORY.createParser(source), withOverriding);
            return null;
        } catch (Exception e) {
            return e.getLocalizedMessage();
        }
    }

    public static String check(final byte[] source) {
        return check(source, true);
    }

    public static String check(final InputStream source, final boolean withOverriding) {
        try {
            doCompile(JSON_FACTORY.createParser(source), withOverriding);
            return null;
        } catch (Exception e) {
            return e.getLocalizedMessage();
        }
    }

    public static String check(final InputStream source) {
        return check(source, true);
    }

    /**
     * Compile a rule from its JSON form to a Map suitable for use by event.ruler.Ruler (elements are surrounded by quotes).
     *
     * @param source rule, as a String
     * @return List of Sub rule which represented as Map
     * @throws IOException if the rule isn't syntactically valid
     */
    public static List<Map<String, List<Patterns>>> compile(final Reader source, boolean withOverriding)
            throws IOException {
        return doCompile(JSON_FACTORY.createParser(source), withOverriding);
    }

    public static List<Map<String, List<Patterns>>> compile(final Reader source) throws IOException {
        return compile(source, true);
    }


    public static List<Map<String, List<Patterns>>> compile(final String source, boolean withOverriding)
            throws IOException {
        return doCompile(JSON_FACTORY.createParser(source), withOverriding);
    }

    public static List<Map<String, List<Patterns>>> compile(final String source) throws IOException {
        return compile(source, true);
    }

    public static List<Map<String, List<Patterns>>> compile(final byte[] source, boolean withOverriding)
            throws IOException {
        return doCompile(JSON_FACTORY.createParser(source), withOverriding);
    }

    public static List<Map<String, List<Patterns>>> compile(final byte[] source) throws IOException {
        return compile(source, true);
    }

    public static List<Map<String, List<Patterns>>> compile(final InputStream source, final boolean withOverriding)
            throws IOException {
        return doCompile(JSON_FACTORY.createParser(source), withOverriding);
    }

    public static List<Map<String, List<Patterns>>> compile(final InputStream source) throws IOException {
        return compile(source, true);
    }

    private static List<Map<String, List<Patterns>>> doCompile(final JsonParser parser, boolean withOverriding) throws IOException {
        final Path path = new Path();
        final List<Map<String, List<Patterns>>> rules = new ArrayList<>();
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            barf(parser, "Filter is not an object");
        }
        parseObject(rules, path, parser, true, withOverriding);
        parser.close();
        return rules;
    }

    private static void parseObject(final List<Map<String, List<Patterns>>> rules,
                                    final Path path,
                                    final JsonParser parser,
                                    final boolean withQuotes,
                                    final boolean withOverriding) throws IOException {

        boolean fieldsPresent = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            fieldsPresent = true;

            // field name
            final String stepName = parser.getCurrentName();

            // If it is "$or" primitive, we should bypass that primitive itself in the path as it is not
            // a real field name, it is just used to describe the "$or" relationship among object in the followed array.
            if (stepName.equals(Constants.OR_RELATIONSHIP_KEYWORD)) {
                parseIntoOrRelationship(rules, path, parser, withQuotes, withOverriding);
                // all Objects in $or array have been handled in above function, just bypass below logic.
                continue;
            }

            switch (parser.nextToken()) {
            case START_OBJECT:
                path.push(stepName);
                parseObject(rules, path, parser, withQuotes, withOverriding);
                path.pop();
                break;

            case START_ARRAY:
                writeRules(rules, path.extendedName(stepName), parser, withQuotes, withOverriding);
                break;

            default:
                barf(parser, String.format("\"%s\" must be an object or an array", stepName));
            }
        }
        if (!fieldsPresent) {
            barf(parser, "Empty objects are not allowed");
        }
    }

    private static void parseObjectInsideOrRelationship(final List<Map<String, List<Patterns>>> rules,
                                                        final Path path,
                                                        final JsonParser parser,
                                                        final boolean withQuotes,
                                                        final boolean withOverriding) throws IOException {

        boolean fieldsPresent = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            fieldsPresent = true;

            // field name
            final String stepName = parser.getCurrentName();

            // If it is "$or" primitive, we should bypass the "$or" primitive itself in the path as it is not
            // a real step name, it is just used to describe the "$or" relationship among object in the followed Array.
            if (stepName.equals(Constants.OR_RELATIONSHIP_KEYWORD)) {
                parseIntoOrRelationship(rules, path, parser, withQuotes, withOverriding);
                continue;
            }

            if (Constants.RESERVED_FIELD_NAMES_IN_OR_RELATIONSHIP.contains(stepName)) {
                barf(parser, stepName +
                        " is Ruler reserved fieldName which cannot be used inside "
                        + Constants.OR_RELATIONSHIP_KEYWORD + ".");
            }

            switch (parser.nextToken()) {
                case START_OBJECT:
                    path.push(stepName);
                    parseObjectInsideOrRelationship(rules, path, parser, withQuotes, withOverriding);
                    path.pop();
                    break;

                case START_ARRAY:
                    writeRules(rules, path.extendedName(stepName), parser, withQuotes, withOverriding);
                    break;

                default:
                    barf(parser, String.format("\"%s\" must be an object or an array", stepName));
            }
        }
        if (!fieldsPresent) {
            barf(parser, "Empty objects are not allowed");
        }
    }

    /**
     * This function is to parse the "$or" relationship described as an array, each object in the array will be
     * interpreted as "$or" relationship, for example:
     * {
     *     "detail": {
     *         "$or" : [
     *             {"c-count": [ { "numeric": [ ">", 0, "<=", 5 ] } ]},
     *             {"d-count": [ { "numeric": [ "<", 10 ] } ]},
     *             {"x-limit": [ { "numeric": [ "=", 3.018e2 ] } ]}
     *         ]
     *     }
     * }
     * above rule will be interpreted as or effect: ("detail.c-count" || "detail.d-count" || detail.x-limit"),
     * The result will be described as the list of Map, each Map represents a sub rule composed of fields of patterns in
     * that or condition path, for example:
     * [
     *   {detail.c-count=[38D7EA4C68000/38D7EA512CB40:true/false]},
     *   {detail.d-count=[0000000000000/38D7EA55F1680:false/true]},
     *   {detail.x-limit=[VP:38D7EB6C39A40]}
     * ]
     * This List will be added into Ruler with the same rule name to demonstrate the "or" effects inside Ruler state
     * machine.
     */
    private static void parseIntoOrRelationship(final List<Map<String, List<Patterns>>> rules,
                                                final Path path,
                                                final JsonParser parser,
                                                final boolean withQuotes,
                                                final boolean withOverriding) throws IOException {

        final List<Map<String, List<Patterns>>> currentRules = deepCopyRules(rules);
        rules.clear();

        JsonToken token = parser.nextToken();
        if (token != JsonToken.START_ARRAY) {
            barf(parser, "It must be an Array followed with " + Constants.OR_RELATIONSHIP_KEYWORD + ".");
        }

        int loopCnt = 0;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            loopCnt++;
            if (token == JsonToken.START_OBJECT) {
                final List<Map<String, List<Patterns>>> newRules = deepCopyRules(currentRules);
                if (newRules.isEmpty()) {
                    newRules.add(new HashMap<>());
                }
                parseObjectInsideOrRelationship(newRules, path, parser, withQuotes, withOverriding);
                rules.addAll(newRules);
            } else {
                barf(parser,
                        "Only JSON object is allowed in array of " + Constants.OR_RELATIONSHIP_KEYWORD + " relationship.");
            }
        }
        if (loopCnt < 2) {
            barf(parser, "There must have at least 2 Objects in " + Constants.OR_RELATIONSHIP_KEYWORD + " relationship.");
        }
    }

    // This is not exactly the real deep copy because here we only need deep copy at List element level,
    private static List deepCopyRules(final List<Map<String, List<Patterns>>> rules) {
        return rules.stream().map(rule -> new HashMap(rule)).collect(Collectors.toList());
    }

    private static void writeRules(final List<Map<String, List<Patterns>>> rules,
                                   final String name,
                                   final JsonParser parser,
                                   final boolean withQuotes,
                                   final boolean withOverriding) throws IOException {

        JsonToken token;
        final List<Patterns> values = new ArrayList<>();

        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            switch (token) {
            case START_OBJECT:
                values.add(processMatchExpression(parser));
                break;

            case VALUE_STRING:
                final String toMatch = parser.getText();

                final Range ipRange = CIDR.ipToRangeIfPossible(toMatch);
                if (ipRange != null) {
                    values.add(ipRange);
                } else if (withQuotes) {
                    values.add(Patterns.exactMatch('"' + toMatch + '"'));
                } else {
                    values.add(Patterns.exactMatch(toMatch));
                }
                break;

            case VALUE_NUMBER_FLOAT:
            case VALUE_NUMBER_INT:
                /*
                 * If the rule specifies a match to a number, we'll insert matchers for both the
                 *  literal expression and the ComparableNumber form. But the number might not
                 *  be representable as a ComparableNumber, for example an AWS account number,
                 *  so make that condition survivable.
                 */
                try {
                    values.add(Patterns.numericEquals(parser.getText()));
                } catch (Exception e) {
                    // no-op
                }
                values.add(Patterns.exactMatch(parser.getText()));
                break;

            case VALUE_NULL:
            case VALUE_TRUE:
            case VALUE_FALSE:
                values.add(Patterns.exactMatch(parser.getText()));
                break;

            default:
                barf(parser, "Match value must be String, number, true, false, or null");
            }
        }
        if (values.isEmpty()) {
            barf(parser, "Empty arrays are not allowed");
        }

        // If the rules list is empty, add the first rule
        if (rules.isEmpty()) {
            rules.add(new HashMap<>());
        }
        for (Map<String, List<Patterns>> rule : rules) {
            if (!withOverriding && rule.containsKey(name)) {
                barf(parser, String.format("Path `%s` cannot be allowed multiple times", name));
            }
            rule.put(name, values);
        }
    }

    // Used to be, the format was
    //      "field-name": [ "val1", "val2" ]
    // now it's like
    //      "field-name": [ "val1", { "prefix": "pref1" }, { "anything-but": "not-this" } ]
    //
    private static Patterns processMatchExpression(final JsonParser parser) throws IOException {
        final JsonToken matchTypeToken = parser.nextToken();
        if (matchTypeToken != JsonToken.FIELD_NAME) {
            barf(parser, "Match expression name not found");
        }
        final String matchTypeName = parser.getCurrentName();
        if (Constants.EXACT_MATCH.equals(matchTypeName)) {
            final JsonToken prefixToken = parser.nextToken();
            if (prefixToken != JsonToken.VALUE_STRING) {
                barf(parser, "exact match pattern must be a string");
            }
            final Patterns pattern = Patterns.exactMatch('"' + parser.getText() + '"');
            if (parser.nextToken() != JsonToken.END_OBJECT) {
                barf(parser, "Only one key allowed in match expression");
            }
            return pattern;
        } else if (Constants.PREFIX_MATCH.equals(matchTypeName)) {
            final JsonToken prefixToken = parser.nextToken();
            if (prefixToken == JsonToken.START_OBJECT) {
                return processPrefixEqualsIgnoreCaseExpression(parser);
            }

            if (prefixToken != JsonToken.VALUE_STRING) {
                barf(parser, "prefix match pattern must be a string");
            }
            final Patterns pattern = Patterns.prefixMatch('"' + parser.getText()); // note no trailing quote
            if (parser.nextToken() != JsonToken.END_OBJECT) {
                barf(parser, "Only one key allowed in match expression");
            }
            return pattern;
        } else if (Constants.SUFFIX_MATCH.equals(matchTypeName)) {
            final JsonToken suffixToken = parser.nextToken();
            if (suffixToken == JsonToken.START_OBJECT) {
                return processSuffixEqualsIgnoreCaseExpression(parser);
            }

            if (suffixToken != JsonToken.VALUE_STRING) {
                barf(parser, "suffix match pattern must be a string");
            }
            final Patterns pattern = Patterns.suffixMatch(parser.getText() + '"'); // note no beginning quote
            if (parser.nextToken() != JsonToken.END_OBJECT) {
                barf(parser, "Only one key allowed in match expression");
            }
            return pattern;
        } else if (Constants.NUMERIC.equals(matchTypeName)) {
            final JsonToken numericalExpressionToken = parser.nextToken();
            if (numericalExpressionToken != JsonToken.START_ARRAY) {
                barf(parser, "Value of " + Constants.NUMERIC + " must be an array.");
            }
            Patterns range = processNumericMatchExpression(parser);
            if (parser.nextToken() != JsonToken.END_OBJECT) {
                tooManyElements(parser);
            }
            return range;
        } else if (Constants.ANYTHING_BUT_MATCH.equals(matchTypeName)) {

            MatchType matchType = MatchType.ANYTHING_BUT;
            JsonToken anythingButExpressionToken = parser.nextToken();
            if (anythingButExpressionToken == JsonToken.START_OBJECT) {

                // there are a limited set of things we can apply Anything-But to
                final JsonToken anythingButObject = parser.nextToken();
                if (anythingButObject != JsonToken.FIELD_NAME) {
                    barf(parser, "Anything-But expression name not found");
                }
                final String anythingButObjectOp = parser.getCurrentName();
                switch (anythingButObjectOp) {
                    case Constants.EQUALS_IGNORE_CASE:
                        matchType = MatchType.ANYTHING_BUT_IGNORE_CASE;
                        break;
                    case Constants.PREFIX_MATCH:
                        matchType = MatchType.ANYTHING_BUT_PREFIX;
                        break;
                    case Constants.SUFFIX_MATCH:
                        matchType = MatchType.ANYTHING_BUT_SUFFIX;
                        break;
                    case Constants.WILDCARD:
                        matchType = MatchType.ANYTHING_BUT_WILDCARD;
                        break;
                    default:
                        barf(parser, "Unsupported anything-but pattern: " + anythingButObjectOp);
                }

                // Step into anything-but's equals-ignore-case/prefix/suffix/wildcard
                anythingButExpressionToken = parser.nextToken();
            }

            if (anythingButExpressionToken != JsonToken.START_ARRAY &&
                    anythingButExpressionToken != JsonToken.VALUE_STRING &&
                    anythingButExpressionToken != JsonToken.VALUE_NUMBER_FLOAT &&
                    anythingButExpressionToken != JsonToken.VALUE_NUMBER_INT) {
                barf(parser, "Value of " +
                        Constants.ANYTHING_BUT_MATCH + " must be an array or single string/number value.");
            }

            Patterns anythingBut;
            if (anythingButExpressionToken == JsonToken.START_ARRAY) {
                if (matchType == MatchType.ANYTHING_BUT) {
                    anythingBut = processAnythingButListMatchExpression(parser);
                } else {
                    anythingBut = processAnythingButValuesSetMatchExpression(parser, matchType);
                }
            } else {
                if (matchType == MatchType.ANYTHING_BUT) {
                    anythingBut = processAnythingButMatchExpression(parser, anythingButExpressionToken);
                } else {
                    anythingBut = processAnythingButValuesSetSingleValueMatchExpression(parser, anythingButExpressionToken, matchType);
                }
            }

            if (parser.nextToken() != JsonToken.END_OBJECT) {
                tooManyElements(parser);
            }
            // Complete the object closure when a set is present
            if (matchType != MatchType.ANYTHING_BUT && parser.nextToken() != JsonToken.END_OBJECT) {
                tooManyElements(parser);
            }

            return anythingBut;
        } else if (Constants.EXISTS_MATCH.equals(matchTypeName)) {
            return processExistsExpression(parser);
        } else if (Constants.CIDR.equals(matchTypeName)) {
            final JsonToken cidrToken = parser.nextToken();
            if (cidrToken != JsonToken.VALUE_STRING) {
                barf(parser, "prefix match pattern must be a string");
            }
            final Range cidr = CIDR.cidr(parser.getText());
            if (parser.nextToken() != JsonToken.END_OBJECT) {
                barf(parser, "Only one key allowed in match expression");
            }
            return cidr;
        } else if (Constants.EQUALS_IGNORE_CASE.equals(matchTypeName)) {
            final JsonToken equalsIgnoreCaseToken = parser.nextToken();
            if (equalsIgnoreCaseToken != JsonToken.VALUE_STRING) {
                barf(parser, "equals-ignore-case match pattern must be a string");
            }
            final Patterns pattern = Patterns.equalsIgnoreCaseMatch('"' + parser.getText() + '"');
            if (parser.nextToken() != JsonToken.END_OBJECT) {
                barf(parser, "Only one key allowed in match expression");
            }
            return pattern;
        } else if (Constants.WILDCARD.equals(matchTypeName)) {
            final JsonToken wildcardToken = parser.nextToken();
            if (wildcardToken != JsonToken.VALUE_STRING) {
                barf(parser, "wildcard match pattern must be a string");
            }
            final String parserText = parser.getText();
            String value = '"' + parserText + '"';
            try {
                getParser().parse(MatchType.WILDCARD, value);
            } catch (ParseException e) {
                barf(parser, e.getLocalizedMessage());
            }
            final Patterns pattern = Patterns.wildcardMatch(value);
            if (parser.nextToken() != JsonToken.END_OBJECT) {
                barf(parser, "Only one key allowed in match expression");
            }
            return pattern;
        } else {
            barf(parser, "Unrecognized match type " + matchTypeName);
            return null; // unreachable statement, but java can't see that?
        }
    }

    private static Patterns processPrefixEqualsIgnoreCaseExpression(final JsonParser parser) throws IOException {
        final JsonToken prefixObject = parser.nextToken();
        if (prefixObject != JsonToken.FIELD_NAME) {
            barf(parser, "Prefix expression name not found");
        }

        final String prefixObjectOp = parser.getCurrentName();
        if (!Constants.EQUALS_IGNORE_CASE.equals(prefixObjectOp)) {
            barf(parser, "Unsupported prefix pattern: " + prefixObjectOp);
        }

        final JsonToken prefixEqualsIgnoreCase = parser.nextToken();
        if (prefixEqualsIgnoreCase != JsonToken.VALUE_STRING) {
            barf(parser, "equals-ignore-case match pattern must be a string");
        }
        final Patterns pattern = Patterns.prefixEqualsIgnoreCaseMatch('"' + parser.getText());
        if (parser.nextToken() != JsonToken.END_OBJECT) {
            barf(parser, "Only one key allowed in match expression");
        }
        if (parser.nextToken() != JsonToken.END_OBJECT) {
            barf(parser, "Only one key allowed in match expression");
        }
        return pattern;
    }

    private static Patterns processSuffixEqualsIgnoreCaseExpression(final JsonParser parser) throws IOException {
        final JsonToken suffixObject = parser.nextToken();
        if (suffixObject != JsonToken.FIELD_NAME) {
            barf(parser, "Suffix expression name not found");
        }

        final String suffixObjectOp = parser.getCurrentName();
        if (!Constants.EQUALS_IGNORE_CASE.equals(suffixObjectOp)) {
            barf(parser, "Unsupported suffix pattern: " + suffixObjectOp);
        }

        final JsonToken suffixEqualsIgnoreCase = parser.nextToken();
        if (suffixEqualsIgnoreCase != JsonToken.VALUE_STRING) {
            barf(parser, "equals-ignore-case match pattern must be a string");
        }
        final Patterns pattern = Patterns.suffixEqualsIgnoreCaseMatch(parser.getText() + '"');
        if (parser.nextToken() != JsonToken.END_OBJECT) {
            barf(parser, "Only one key allowed in match expression");
        }
        if (parser.nextToken() != JsonToken.END_OBJECT) {
            barf(parser, "Only one key allowed in match expression");
        }
        return pattern;
    }

    private static Patterns processAnythingButListMatchExpression(JsonParser parser) throws JsonParseException {
        JsonToken token;
        Set<String> values = new HashSet<>();
        boolean hasNumber = false;
        boolean hasString = false;
        try {
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                switch (token) {
                    case VALUE_STRING:
                        values.add('"' + parser.getText() + '"');
                        hasString = true;
                        break;
                    case VALUE_NUMBER_FLOAT:
                    case VALUE_NUMBER_INT:
                        values.add(ComparableNumber.generate(parser.getText()));
                        hasNumber = true;
                        break;
                    default:
                        barf(parser, "Inside anything but list, start|null|boolean is not supported.");
                }
            }
        } catch (IllegalArgumentException | IOException e) {
            barf(parser, e.getMessage());
        }

        if ((hasNumber && hasString) || (!hasNumber && !hasString)) {
            barf(parser, "Inside anything but list, either all values are number or string, " +
                    "mixed type is not supported");
        }
        return AnythingBut.anythingButMatch(values, hasNumber);
    }

    private static Patterns processAnythingButValuesSetMatchExpression(JsonParser parser, MatchType matchType)
            throws JsonParseException {
        JsonToken token;
        Set<String> values = new HashSet<>();
        try {
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                switch (token) {
                    case VALUE_STRING:
                        String text = parser.getText();
                        if ((matchType == MatchType.ANYTHING_BUT_PREFIX || matchType ==  MatchType.ANYTHING_BUT_SUFFIX)
                                && text.isEmpty()) {
                            barf(parser, "Null prefix/suffix not allowed");
                        }
                        values.add(generateValueBasedOnMatchType(text, matchType));
                        break;
                    default:
                        if (matchType == MatchType.ANYTHING_BUT_IGNORE_CASE) {
                            barf(parser, "Inside anything-but/equals-ignore-case list, number|start|null|boolean is not supported.");
                        } else if (matchType == MatchType.ANYTHING_BUT_WILDCARD) {
                            barf(parser, "wildcard match pattern must be a string");
                        } else {
                            barf(parser, "prefix/suffix match pattern must be a string");
                        }
                }
            }
        } catch (IllegalArgumentException | IOException e) {
            barf(parser, e.getMessage());
        }

        switch (matchType) {
            case ANYTHING_BUT_IGNORE_CASE: return Patterns.anythingButIgnoreCaseMatch(values);
            case ANYTHING_BUT_PREFIX: return Patterns.anythingButPrefix(values);
            case ANYTHING_BUT_SUFFIX: return Patterns.anythingButSuffix(values);
            case ANYTHING_BUT_WILDCARD:
                for (String value : values) {
                    try {
                        getParser().parse(MatchType.ANYTHING_BUT_WILDCARD, value);
                    } catch (ParseException e) {
                        barf(parser, e.getLocalizedMessage());
                    }
                }
                return Patterns.anythingButWildcard(values);
            // Not barfing as this is a code bug rather than bad JSON.
            default: throw new IllegalArgumentException("processAnythingButValuesSetMatchExpression received invalid matchType of " + matchType);
        }
    }

    private static Patterns processAnythingButMatchExpression(JsonParser parser,
                                                              JsonToken anythingButExpressionToken) throws IOException {
        Set<String> values = new HashSet<>();
        boolean hasNumber = false;
        switch (anythingButExpressionToken) {
            case VALUE_STRING:
                values.add('"' + parser.getText() + '"');
                break;
            case VALUE_NUMBER_FLOAT:
            case VALUE_NUMBER_INT:
                values.add(ComparableNumber.generate(parser.getText()));
                hasNumber = true;
                break;
            default:
                barf(parser, "Inside anything-but list, start|null|boolean is not supported.");
        }
        return AnythingBut.anythingButMatch(values, hasNumber);
    }

    private static Patterns processAnythingButValuesSetSingleValueMatchExpression(JsonParser parser,
                JsonToken anythingButExpressionToken, MatchType matchType) throws IOException {
        Set<String> values = new HashSet<>();
        switch (anythingButExpressionToken) {
            case VALUE_STRING:
                String text = parser.getText();
                if ((matchType == MatchType.ANYTHING_BUT_PREFIX || matchType ==  MatchType.ANYTHING_BUT_SUFFIX)
                        && text.isEmpty()) {
                    barf(parser, "Null prefix/suffix not allowed");
                }
                values.add(generateValueBasedOnMatchType(text, matchType));
                break;
            default:
                if (matchType == MatchType.ANYTHING_BUT_IGNORE_CASE) {
                    barf(parser, "Inside anything-but/equals-ignore-case list, number|start|null|boolean is not supported.");
                } else if (matchType == MatchType.ANYTHING_BUT_WILDCARD) {
                    barf(parser, "wildcard match pattern must be a string");
                } else {
                    barf(parser, "prefix/suffix match pattern must be a string");
                }
        }

        switch (matchType) {
            case ANYTHING_BUT_IGNORE_CASE: return Patterns.anythingButIgnoreCaseMatch(values);
            case ANYTHING_BUT_PREFIX: return Patterns.anythingButPrefix(values);
            case ANYTHING_BUT_SUFFIX: return Patterns.anythingButSuffix(values);
            case ANYTHING_BUT_WILDCARD:
                try {
                    getParser().parse(MatchType.ANYTHING_BUT_WILDCARD, values.iterator().next());
                } catch (ParseException e) {
                    barf(parser, e.getLocalizedMessage());
                }
                return Patterns.anythingButWildcard(values);
            // Not barfing as this is a code bug rather than bad JSON.
            default: throw new IllegalArgumentException("processAnythingButValuesSetSingleValueMatchExpression received invalid matchType of " + matchType);
        }
    }

    private static String generateValueBasedOnMatchType(String text, MatchType matchType) {
        switch (matchType) {
            case PREFIX:
            case PREFIX_EQUALS_IGNORE_CASE:
            case ANYTHING_BUT_PREFIX:
                return '"' + text;
            case SUFFIX:
            case SUFFIX_EQUALS_IGNORE_CASE:
            case ANYTHING_BUT_SUFFIX:
                return text + '"';
            default:
                return '"' + text + '"';
        }
    }

    private static Patterns processNumericMatchExpression(final JsonParser parser) throws IOException {
        JsonToken token = parser.nextToken();
        if (token != JsonToken.VALUE_STRING) {
            barf(parser, "Invalid member in numeric match: " + parser.getText());
        }
        String operator = parser.getText();
        token = parser.nextToken();
        try {
            if (Constants.EQ.equals(operator)) {
                if (!token.isNumeric()) {
                    barf(parser, "Value of equals must be numeric");
                }
                final String val = parser.getText();
                if (parser.nextToken() != JsonToken.END_ARRAY) {
                    tooManyElements(parser);
                }
                return Patterns.numericEquals(val);
            } else if (Constants.GE.equals(operator)) {
                if (!token.isNumeric()) {
                    barf(parser, "Value of >= must be numeric");
                }
                final String val = parser.getText();
                token = parser.nextToken();
                if (token == JsonToken.END_ARRAY) {
                    return Range.greaterThanOrEqualTo(val);
                }
                return completeNumericRange(parser, token, val, false);

            } else if (Constants.GT.equals(operator)) {
                if (!token.isNumeric()) {
                    barf(parser, "Value of > must be numeric");
                }
                final String val = parser.getText();
                token = parser.nextToken();
                if (token == JsonToken.END_ARRAY) {
                    return Range.greaterThan(val);
                }
                return completeNumericRange(parser, token, val, true);

            } else if (Constants.LE.equals(operator)) {
                if (!token.isNumeric()) {
                    barf(parser, "Value of <= must be numeric");
                }
                final String top = parser.getText();
                if (parser.nextToken() != JsonToken.END_ARRAY) {
                    tooManyElements(parser);
                }
                return Range.lessThanOrEqualTo(top);

            } else if (Constants.LT.equals(operator)) {
                if (!token.isNumeric()) {
                    barf(parser, "Value of < must be numeric");
                }
                final String top = parser.getText();
                if (parser.nextToken() != JsonToken.END_ARRAY) {
                    tooManyElements(parser);
                }
                return Range.lessThan(top);
            } else {
                barf(parser, "Unrecognized numeric range operator: " + operator);
            }
        } catch (IllegalArgumentException e) {
            barf(parser, e.getMessage());
        }

        barf(parser, "Reached a line which is supposed completely unreachable.");
        return null;  // completely unreachable
    }

    private static Patterns completeNumericRange(final JsonParser parser,
                                                 final JsonToken token,
                                                 final String bottom,
                                                 final boolean openBottom) throws IOException {
        if (token != JsonToken.VALUE_STRING) {
            barf(parser, "Bad value in numeric range: " + parser.getText());
        }
        final String operator = parser.getText();
        boolean openTop = false;

        if (Constants.LT.equals(operator)) {
            openTop = true;
        } else if (!Constants.LE.equals(operator)) {
            barf(parser, "Bad numeric range operator: " + operator);
        }
        if (!parser.nextToken().isNumeric()) {
            barf(parser, "Value of " + operator + " must be numeric");
        }
        final String top = parser.getText();
        if (parser.nextToken() != JsonToken.END_ARRAY) {
            barf(parser, "Too many terms in numeric range expression");
        }
        return Range.between(bottom, openBottom, top, openTop);
    }

    private static Patterns processExistsExpression(final JsonParser parser) throws IOException {
        final JsonToken existsToken = parser.nextToken();
        Patterns existsPattern;

        if (existsToken == JsonToken.VALUE_TRUE) {
            existsPattern = Patterns.existencePatterns();
        } else if (existsToken == JsonToken.VALUE_FALSE) {
            existsPattern = Patterns.absencePatterns();
        } else {
            barf(parser, "exists match pattern must be either true or false.");
            return null;
        }

        if (parser.nextToken() != JsonToken.END_OBJECT) {
            barf(parser, "Only one key allowed in match expression");
            return null;
        }
        return existsPattern;
    }

    private static void tooManyElements(final JsonParser parser) throws JsonParseException {
        barf(parser, "Too many elements in numeric expression");
    }

    private static void barf(final JsonParser parser, final String message) throws JsonParseException {
        throw new JsonParseException(parser, message, parser.getCurrentLocation());
    }
}
