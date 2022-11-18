package software.amazon.event.ruler;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

final class Constants {

  private Constants() {
    throw new UnsupportedOperationException();
  }

  static final String EXACT_MATCH = "exactly";
  static final String EQUALS_IGNORE_CASE = "equals-ignore-case";
  static final String PREFIX_MATCH = "prefix";
  static final String SUFFIX_MATCH = "suffix";
  static final String ANYTHING_BUT_MATCH = "anything-but";
  static final String EXISTS_MATCH = "exists";
  static final String WILDCARD = "wildcard";
  static final String NUMERIC = "numeric";
  static final String CIDR = "cidr";

  // This is Ruler reserved words to represent the $or relationship among the fields.
  static final String OR_RELATIONSHIP_KEYWORD = "$or";

  static final String EQ = "=";
  static final String LT = "<";
  static final String LE = "<=";
  static final String GT = ">";
  static final String GE = ">=";

  // Use scientific notation to define the double number directly to avoid losing Precision by calculation
  // for example 5000 * 1000 *1000 will be wrongly parsed as 7.05032704E8 by computer.
  static final double FIVE_BILLION = 5E9;

  static final Pattern IPv4_REGEX = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");
  static final Pattern IPv6_REGEX = Pattern.compile("[0-9a-fA-F:]*:[0-9a-fA-F:]*");
  static final byte[] HEX_DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
      'A', 'B', 'C', 'D', 'E', 'F'
  };
  static final byte MAX_DIGIT = 'F';

  static final List<String> RESERVED_FIELD_NAMES_IN_OR_RELATIONSHIP = Arrays.asList(
      EXACT_MATCH,
      EQUALS_IGNORE_CASE,
      PREFIX_MATCH,
      SUFFIX_MATCH,
      ANYTHING_BUT_MATCH,
      EXISTS_MATCH,
      WILDCARD,
      NUMERIC,
      CIDR,
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