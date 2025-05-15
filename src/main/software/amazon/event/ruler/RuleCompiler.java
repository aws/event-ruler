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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static software.amazon.event.ruler.input.DefaultParser.getParser;

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
public final class RuleCompiler {

    private static final JsonFactory JSON_FACTORY = JsonFactory.builder()
            .configure(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION, true)
            .build();

    private RuleCompiler() {
      throw new UnsupportedOperationException("You can't create instance of utility class.");
    }

    /**
     * Verify the syntax of a rule
     * @param source rule, as a Reader
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

    /**
     * Verify the syntax of a rule
     * @param source rule, as a String
     * @return null if the rule is valid, otherwise an error message
     */
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

    /**
     * Verify the syntax of a rule
     * @param source rule, as a byte array
     * @return null if the rule is valid, otherwise an error message
     */
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

    /**
     * Verify the syntax of a rule
     * @param source rule, as an InputStream
     * @return null if the rule is valid, otherwise an error message
     */
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
     * Compile a rule from its JSON form to a Map suitable for use by events.ruler.Ruler (elements are surrounded by quotes).
     *
     * @param source rule, as a Reader
     * @return Map form of rule
     * @throws IOException if the rule isn't syntactically valid
     */
    public static Map<String, List<Patterns>> compile(final Reader source, final boolean withOverriding) throws IOException {
        return doCompile(JSON_FACTORY.createParser(source), withOverriding);
    }

    public static Map<String, List<Patterns>> compile(final Reader source) throws IOException {
        return compile(source, true);
    }

    /**
     * Compile a rule from its JSON form to a Map suitable for use by events.ruler.Ruler (elements are surrounded by quotes).
     *
     * @param source rule, as a String
     * @return Map form of rule
     * @throws IOException if the rule isn't syntactically valid
     */
    public static Map<String, List<Patterns>> compile(final String source, final boolean withOverriding) throws IOException {
        return doCompile(JSON_FACTORY.createParser(source), withOverriding);
    }

    public static Map<String, List<Patterns>> compile(final String source) throws IOException {
        return compile(source, true);
    }

    /**
     * Compile a rule from its JSON form to a Map suitable for use by events.ruler.Ruler (elements are surrounded by quotes).
     *
     * @param source rule, as a byte array
     * @return Map form of rule
     * @throws IOException if the rule isn't syntactically valid
     */
    public static Map<String, List<Patterns>> compile(final byte[] source, final boolean withOverriding) throws IOException {
        return doCompile(JSON_FACTORY.createParser(source), withOverriding);
    }

    public static Map<String, List<Patterns>> compile(final byte[] source) throws IOException {
        return compile(source, true);
    }

    /**
     * Compile a rule from its JSON form to a Map suitable for use by events.ruler.Ruler (elements are surrounded by quotes).
     *
     * @param source rule, as an InputStream
     * @return Map form of rule
     * @throws IOException if the rule isn't syntactically valid
     */
    public static Map<String, List<Patterns>> compile(final InputStream source, final boolean withOverriding) throws IOException {
        return doCompile(JSON_FACTORY.createParser(source), withOverriding);
    }

    public static Map<String, List<Patterns>> compile(final InputStream source) throws IOException {
        return compile(source, true);
    }

    private static Map<String, List<Patterns>> doCompile(final JsonParser parser, final boolean withOverriding) throws IOException {
        final Path path = new Path();
        final Map<String, List<Patterns>> rule = new HashMap<>();
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            barf(parser, "Filter is not an object");
        }
        parseObject(rule, path, parser, true, withOverriding);
        parser.close();
        return rule;
    }

    private static void parseObject(final Map<String, List<Patterns>> rule,
                                    final Path path,
                                    final JsonParser parser,
                                    final boolean withQuotes,
                                    final boolean withOverriding) throws IOException {

        boolean fieldsPresent = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            fieldsPresent = true;

            // field name
            final String stepName = parser.getCurrentName();

            switch (parser.nextToken()) {
            case START_OBJECT:
                path.push(stepName);
                parseObject(rule, path, parser, withQuotes, withOverriding);
                path.pop();
                break;

            case START_ARRAY:
                writeRules(rule, path.extendedName(stepName), parser, withQuotes, withOverriding);
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
                 *  be representble as a ComparableNumber, for example an AWS account number,
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
        if (!withOverriding && rule.containsKey(name)) {
            barf(parser, String.format("Path `%s` cannot be allowed multiple times", name));
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

            boolean isIgnoreCase = false;
            JsonToken anythingButExpressionToken = parser.nextToken();
            if (anythingButExpressionToken == JsonToken.START_OBJECT) {

                // there are a limited set of things we can apply Anything-But to
                final JsonToken anythingButObject = parser.nextToken();
                if (anythingButObject != JsonToken.FIELD_NAME) {
                    barf(parser, "Anything-But expression name not found");
                }
                final String anythingButObjectOp = parser.getCurrentName();
                final boolean isPrefix = Constants.PREFIX_MATCH.equals(anythingButObjectOp);
                final boolean isSuffix = Constants.SUFFIX_MATCH.equals(anythingButObjectOp);
                isIgnoreCase = Constants.EQUALS_IGNORE_CASE.equals(anythingButObjectOp);
                if(!isIgnoreCase) {
                    if (!isPrefix && !isSuffix) {
                        barf(parser, "Unsupported anything-but pattern: " + anythingButObjectOp);
                    }
                    final JsonToken anythingButParamType = parser.nextToken();
                    if (anythingButParamType != JsonToken.VALUE_STRING) {
                        barf(parser, "prefix/suffix match pattern must be a string");
                    }
                    final String text = parser.getText();
                    if (text.isEmpty()) {
                        barf(parser, "Null prefix/suffix not allowed");
                    }
                    if (parser.nextToken() != JsonToken.END_OBJECT) {
                        barf(parser, "Only one key allowed in match expression");
                    }
                    if (parser.nextToken() != JsonToken.END_OBJECT) {
                        barf(parser, "Only one key allowed in match expression");
                    }
                    if(isPrefix) {
                       return Patterns.anythingButPrefix('"' + text); // note no trailing quote
                    } else {
                       return Patterns.anythingButSuffix(text + '"'); // note no leading quote
                    }
                } else {
                    // Step into the anything-but's ignore-case
                    anythingButExpressionToken = parser.nextToken();
                }

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
                if(isIgnoreCase) {
                   anythingBut = processAnythingButEqualsIgnoreCaseListMatchExpression(parser);
                } else {
                   anythingBut = processAnythingButListMatchExpression(parser);
                }
            } else {
                if(isIgnoreCase) {
                   anythingBut = processAnythingButEqualsIgnoreCaseMatchExpression(parser, anythingButExpressionToken);
                } else {
                   anythingBut = processAnythingButMatchExpression(parser, anythingButExpressionToken);
                }
            }

            if (parser.nextToken() != JsonToken.END_OBJECT) {
                tooManyElements(parser);
            }
            // If its an ignore-case, we have another
            // object end to consume...
            if(isIgnoreCase && parser.nextToken() != JsonToken.END_OBJECT) {
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

    private static Patterns processAnythingButEqualsIgnoreCaseListMatchExpression(JsonParser parser) throws JsonParseException {
        JsonToken token;
        Set<String> values = new HashSet<>();
        boolean hasNumber = false;
        try {
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                switch (token) {
                    case VALUE_STRING:
                        values.add('"' + parser.getText() + '"');
                        break;
                    default:
                        barf(parser, "Inside anything-but/ignore-case list, number|start|null|boolean is not supported.");
                }
            }
        } catch (IllegalArgumentException | IOException e) {
            barf(parser, e.getMessage());
        }

        return AnythingButValuesSet.anythingButIgnoreCaseMatch(values);
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

    private static Patterns processAnythingButEqualsIgnoreCaseMatchExpression(JsonParser parser,
                                                              JsonToken anythingButExpressionToken) throws IOException {
        Set<String> values = new HashSet<>();
        switch (anythingButExpressionToken) {
            case VALUE_STRING:
                values.add('"' + parser.getText() + '"');
                break;
            default:
                barf(parser, "Inside anything-but/ignore-case list, number|start|null|boolean is not supported.");
        }
        return AnythingButValuesSet.anythingButIgnoreCaseMatch(values);
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
                        values.add(Patterns.numericEquals(parser.getText()));
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
