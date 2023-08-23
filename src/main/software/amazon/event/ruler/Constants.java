package software.amazon.event.ruler;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

final class Constants {

  private Constants() {
    throw new UnsupportedOperationException("You can't create instance of utility class.");
  }

  final static String EXACT_MATCH = "exactly";
  final static String EQUALS_IGNORE_CASE = "equals-ignore-case";
  final static String PREFIX_MATCH = "prefix";
  final static String SUFFIX_MATCH = "suffix";
  final static String ANYTHING_BUT_MATCH = "anything-but";
  final static String EXISTS_MATCH = "exists";
  final static String WILDCARD = "wildcard";
  final static String NUMERIC = "numeric";
  final static String CIDR = "cidr";
  final static String HTML = "html";

  // This is Ruler reserved words to represent the $or relationship among the fields.
  final static String OR_RELATIONSHIP_KEYWORD = "$or";

  final static String EQ = "=";
  final static String LT = "<";
  final static String LE = "<=";
  final static String GT = ">";
  final static String GE = ">=";

  // Use scientific notation to define the double number directly to avoid losing Precision by calculation
  // for example 5000 * 1000 *1000 will be wrongly parsed as 7.05032704E8 by computer.
  final static double FIVE_BILLION = 5E9;

  final static Pattern IPv4_REGEX = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");
  final static Pattern IPv6_REGEX = Pattern.compile("[0-9a-fA-F:]*:[0-9a-fA-F:]*");
  final static byte[] HEX_DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
      'A', 'B', 'C', 'D', 'E', 'F'
  };
  final static byte MAX_DIGIT = 'F';

  final static List<String> RESERVED_FIELD_NAMES_IN_OR_RELATIONSHIP = Arrays.asList(
      EXACT_MATCH,
      EQUALS_IGNORE_CASE,
      PREFIX_MATCH,
      SUFFIX_MATCH,
      ANYTHING_BUT_MATCH,
      EXISTS_MATCH,
      WILDCARD,
      NUMERIC,
      CIDR,
          HTML,
      // Numeric comparisons
      EQ, LT, LE, GT, GE,
      // reserve below keywords for future extension
      "regex",
      // String Comparisons
      "not-wildcard", "not-equals-ignore-case",
      // Date/Time comparisons
      "date-after", "date-on-or-after", "date-before", "date-on-or-before", "in-date-range",
      // IP Address Comparison
      "ip-address-in-range", "ip-address-not-in-range"
  );
}
