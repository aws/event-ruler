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

  // This is Ruler reserved words to represent the $or relationship among the fields.
  final static String OR_RELATIONSHIP_KEYWORD = "$or";

  final static String EQ = "=";
  final static String LT = "<";
  final static String LE = "<=";
  final static String GT = ">";
  final static String GE = ">=";

  final static Pattern IPv4_REGEX = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");
  final static Pattern IPv6_REGEX = Pattern.compile("[0-9a-fA-F:]*:[0-9a-fA-F:]*");
  final static byte[] HEX_DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
      'A', 'B', 'C', 'D', 'E', 'F'
  };
  final static byte MAX_HEX_DIGIT = HEX_DIGITS[HEX_DIGITS.length - 1]; // F
  final static byte MIN_HEX_DIGIT = HEX_DIGITS[0]; // 0

  static final byte[] BASE64_DIGITS = {
          // numbers are ordered intentionally to based on ascii table value
          '+', '/',
          '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
          'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
          'W', 'X', 'Y', 'Z',
          'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
          'w', 'x', 'y', 'z',
  };

  final static byte MAX_NUM_DIGIT = BASE64_DIGITS[BASE64_DIGITS.length - 1]; // z
  final static byte MIN_NUM_DIGIT = BASE64_DIGITS[0]; // +

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
