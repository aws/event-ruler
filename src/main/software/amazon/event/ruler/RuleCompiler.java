package software.amazon.event.ruler;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Compiles Rules, expressed in JSON, for use in Ruler.
 * There are two flavors of compilation:
 * 1. Compile a JSON-based Rule into Map of String to List of Patterns which can be used in rulesForEvent,
 *    and has a "check" variant that just checks rules for syntactic accuracy
 * 2. Starting in ListBasedRuleCompiler, does the same thing but expresses field names as List ofString
 *    rather than "."-separated strings for use in the Ruler class, which does not use state machines and
 *    needs to step into the event field by field.
 *
 * Is public so clients can call the check() method to syntax-check filters
 */
public class RuleCompiler {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private RuleCompiler() { }

    /**
     * Verify the syntax of a rule
     * @param source rule, as a Reader
     * @return null if the rule is valid, otherwise an error message
     */
    public static String check(final Reader source) {
        try {
            doCompile(JSON_FACTORY.createParser(source));
            return null;
        } catch (Exception e) {
            return e.getLocalizedMessage();
        }
    }

    /**
     * Verify the syntax of a rule
     * @param source rule, as a String
     * @return null if the rule is valid, otherwise an error message
     */
    public static String check(final String source) {
        try {
            doCompile(JSON_FACTORY.createParser(source));
            return null;
        } catch (Exception e) {
            return e.getLocalizedMessage();
        }
    }

    /**
     * Verify the syntax of a rule
     * @param source rule, as a byte array
     * @return null if the rule is valid, otherwise an error message
     */
    public static String check(final byte[] source) {
        try {
            doCompile(JSON_FACTORY.createParser(source));
            return null;
        } catch (Exception e) {
            return e.getLocalizedMessage();
        }
    }

    /**
     * Verify the syntax of a rule
     * @param source rule, as an InputStream
     * @return null if the rule is valid, otherwise an error message
     */
    public static String check(final InputStream source) {
        try {
            doCompile(JSON_FACTORY.createParser(source));
            return null;
        } catch (Exception e) {
            return e.getLocalizedMessage();
        }
    }

    /**
     * Compile a rule from its JSON form to a Map suitable for use by events.ruler.Ruler (elements are surrounded by quotes).
     *
     * @param source rule, as a Reader
     * @return Map form of rule
     * @throws IOException if the rule isn't syntactically valid
     */
    public static Map<String, List<Patterns>> compile(final Reader source) throws IOException {
        return doCompile(JSON_FACTORY.createParser(source));
    }

    /**
     * Compile a rule from its JSON form to a Map suitable for use by events.ruler.Ruler (elements are surrounded by quotes).
     *
     * @param source rule, as a String
     * @return Map form of rule
     * @throws IOException if the rule isn't syntactically valid
     */
    public static Map<String, List<Patterns>> compile(final String source) throws IOException {
        return doCompile(JSON_FACTORY.createParser(source));
    }

    /**
     * Compile a rule from its JSON form to a Map suitable for use by events.ruler.Ruler (elements are surrounded by quotes).
     *
     * @param source rule, as a byte array
     * @return Map form of rule
     * @throws IOException if the rule isn't syntactically valid
     */
    public static Map<String, List<Patterns>> compile(final byte[] source) throws IOException {
        return doCompile(JSON_FACTORY.createParser(source));
    }

    /**
     * Compile a rule from its JSON form to a Map suitable for use by events.ruler.Ruler (elements are surrounded by quotes).
     *
     * @param source rule, as an InputStream
     * @return Map form of rule
     * @throws IOException if the rule isn't syntactically valid
     */
    public static Map<String, List<Patterns>> compile(final InputStream source) throws IOException {
        return doCompile(JSON_FACTORY.createParser(source));
    }

    private static Map<String, List<Patterns>> doCompile(final JsonParser parser) throws IOException {
        final Path path = new Path();
        final Map<String, List<Patterns>> rule = new HashMap<>();
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            barf(parser, "Filter is not an object");
        }
        parseObject(rule, path, parser, true);
        parser.close();
        return rule;
    }

    private static void parseObject(final Map<String, List<Patterns>> rule,
                                    final Path path,
                                    final JsonParser parser,
                                    final boolean withQuotes) throws IOException {

        boolean fieldsPresent = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            fieldsPresent = true;

            // field name
            final String stepName = parser.getCurrentName();

            switch (parser.nextToken()) {
            case START_OBJECT:
                path.push(stepName);
                parseObject(rule, path, parser, withQuotes);
                path.pop();
                break;

            case START_ARRAY:
                writeRules(rule, path.extendedName(stepName), parser, withQuotes);
                break;

            default:
                barf(parser, String.format("\"%s\" must be an object or an array", stepName));
            }
        }
        if (!fieldsPresent) {
            barf(parser, "Empty objects are not allowed");
        }
    }

    private static void writeRules(final Map<String, List<Patterns>> rule,
                                   final String name,
                                   final JsonParser parser,
                                   final boolean withQuotes) throws IOException {
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
                 *  be representble as a ComparableNumber, for example an AWS account number,
                 *  so make that condition survivable.
                 */
                try {
                    values.add(Patterns.numericEquals(parser.getDoubleValue()));
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
        rule.put(name, values);
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

            final JsonToken anythingButExpressionToken = parser.nextToken();
            if (anythingButExpressionToken == JsonToken.START_OBJECT) {

                // there are a limited set of things we can apply Anything-But to
                final JsonToken anythingButObject = parser.nextToken();
                if (anythingButObject != JsonToken.FIELD_NAME) {
                    barf(parser, "Anything-But expression name not found");
                }
                final String anythingButObjectOp = parser.getCurrentName();
                if (!Constants.PREFIX_MATCH.equals(anythingButObjectOp)) {
                    barf(parser, "Unsupported anything-but pattern: " + anythingButObjectOp);
                }
                final JsonToken anythingButPrefix = parser.nextToken();
                if (anythingButPrefix != JsonToken.VALUE_STRING) {
                    barf(parser, "prefix match pattern must be a string");
                }
                final String prefixText = parser.getText();
                if (prefixText.isEmpty()) {
                    barf(parser, "Null prefix not allowed");
                }
                final Patterns pattern = Patterns.anythingButPrefix('"' + prefixText); // note no trailing quote
                if (parser.nextToken() != JsonToken.END_OBJECT) {
                    barf(parser, "Only one key allowed in match expression");
                }
                if (parser.nextToken() != JsonToken.END_OBJECT) {
                    barf(parser, "Only one key allowed in match expression");
                }
                return pattern;
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
                anythingBut = processAnythingButListMatchExpression(parser);
            } else {
                anythingBut = processAnythingButMatchExpression(parser, anythingButExpressionToken);
            }

            if (parser.nextToken() != JsonToken.END_OBJECT) {
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
            final Patterns pattern = Patterns.wildcardMatch('"' + parserText + '"');
            if (parser.nextToken() != JsonToken.END_OBJECT) {
                barf(parser, "Only one key allowed in match expression");
            }
            return pattern;
        } else {
            barf(parser, "Unrecognized match type " + matchTypeName);
            return null; // unreachable statement, but java can't see that?
        }
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
                        values.add(ComparableNumber.generate(parser.getDoubleValue()));
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
                values.add(ComparableNumber.generate(parser.getDoubleValue()));
                hasNumber = true;
                break;
            default:
                barf(parser, "Inside anything-but list, start|null|boolean is not supported.");
        }
        return AnythingBut.anythingButMatch(values, hasNumber);
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
                final double val = parser.getDoubleValue();
                if (parser.nextToken() != JsonToken.END_ARRAY) {
                    tooManyElements(parser);
                }
                return Patterns.numericEquals(val);
            } else if (Constants.GE.equals(operator)) {
                if (!token.isNumeric()) {
                    barf(parser, "Value of >= must be numeric");
                }
                final double val = parser.getDoubleValue();
                token = parser.nextToken();
                if (token == JsonToken.END_ARRAY) {
                    return Range.greaterThanOrEqualTo(val);
                }
                return completeNumericRange(parser, token, val, false);

            } else if (Constants.GT.equals(operator)) {
                if (!token.isNumeric()) {
                    barf(parser, "Value of > must be numeric");
                }
                final double val = parser.getDoubleValue();
                token = parser.nextToken();
                if (token == JsonToken.END_ARRAY) {
                    return Range.greaterThan(val);
                }
                return completeNumericRange(parser, token, val, true);

            } else if (Constants.LE.equals(operator)) {
                if (!token.isNumeric()) {
                    barf(parser, "Value of <= must be numeric");
                }
                final double top = parser.getDoubleValue();
                if (parser.nextToken() != JsonToken.END_ARRAY) {
                    tooManyElements(parser);
                }
                return Range.lessThanOrEqualTo(top);

            } else if (Constants.LT.equals(operator)) {
                if (!token.isNumeric()) {
                    barf(parser, "Value of < must be numeric");
                }
                final double top = parser.getDoubleValue();
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
        return null;  // completely unreachable
    }

    private static Patterns completeNumericRange(final JsonParser parser,
                                                 final JsonToken token,
                                                 final double bottom,
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
        final double top = parser.getDoubleValue();
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

    /**
     * This is a rule parser which will parse rule of JSON format into a map of string list to Patterns list structure
     * which is suitable to be used by Ruler.matches.
     * The only difference in output between ListBasedRuleCompiler.flattenRule and Filter.compile is type and format of
     * Map.key.
     * For example, if input rule is below JSON string:
     * {
     *     "detail" : {
     *         "state" : [ "initializing" ]
     *     }
     * }
     * The key of output MAP by ListBasedRuleCompiler.flattenRule will be a list like: ["detail","state"].
     * The key of output MAP by Filter.compile will be a String: "detail.state".
     */
    public static class ListBasedRuleCompiler {

        private static final JsonFactory JSON_FACTORY = new JsonFactory();

        /**
         * Compile a rule from its JSON form to a Map suitable for use by events.ruler.Ruler
         *
         * @param source rule, as a String
         * @return Map form of rule
         * @throws IOException if the rule isn't syntactically valid
         */
        public static Map<List<String>, List<Patterns>> flattenRule(final String source) throws IOException {
            return doFlattenRule(JSON_FACTORY.createParser(source));
        }

        private static Map<List<String>, List<Patterns>> doFlattenRule(final JsonParser parser) throws IOException {
            final Deque<String> stack = new ArrayDeque<>();
            final Map<List<String>, List<Patterns>> rule = new HashMap<>();
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                barf(parser, "Filter is not an object");
            }
            parseRuleObject(rule, stack, parser, true);
            parser.close();
            return rule;
        }

        private static void parseRuleObject(final Map<List<String>, List<Patterns>> rule,
                                            final Deque<String> stack,
                                            final JsonParser parser,
                                            final boolean withQuotes) throws IOException {

            boolean fieldsPresent = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                fieldsPresent = true;

                // field name
                final String stepName = parser.getCurrentName();

                switch (parser.nextToken()) {
                    case START_OBJECT:
                        stack.addLast(stepName);
                        parseRuleObject(rule, stack, parser, withQuotes);
                        stack.removeLast();
                        break;

                    case START_ARRAY:
                        writeRules(rule, rulePathname(stack, stepName), parser, withQuotes);
                        break;

                    default:
                        barf(parser, String.format("\"%s\" must be an object or an array", stepName));
                }
            }
            if (!fieldsPresent) {
                barf(parser, "Empty objects are not allowed");
            }
        }

        private static void writeRules(final Map<List<String>, List<Patterns>> rule,
                                       final List<String> name,
                                       final JsonParser parser,
                                       final boolean withQuotes) throws IOException {
            JsonToken token;
            final List<Patterns> values = new ArrayList<>();

            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                switch (token) {
                    case START_OBJECT:
                        values.add(processMatchExpression(parser));
                        break;

                    case VALUE_STRING:
                        if (withQuotes) {
                            values.add(Patterns.exactMatch('"' + parser.getText() + '"'));
                        } else {
                            values.add(Patterns.exactMatch(parser.getText()));
                        }
                        break;

                    case VALUE_NUMBER_FLOAT:
                    case VALUE_NUMBER_INT:
                        values.add(Patterns.numericEquals(parser.getDoubleValue()));
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
            rule.put(name, values);
        }

        private static List<String> rulePathname(final Deque<String> path, final String stepName) {
            List<String> sb = new ArrayList<>(path);
            sb.add(stepName);
            return sb;
        }
    }
}
