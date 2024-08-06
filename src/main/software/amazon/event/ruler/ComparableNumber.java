package software.amazon.event.ruler;

import com.fasterxml.jackson.core.io.doubleparser.JavaBigDecimalParser;

import java.math.BigDecimal;

import static software.amazon.event.ruler.Constants.BASE64_DIGITS;
import static software.amazon.event.ruler.Constants.MIN_NUM_DIGIT;

/**
 * Represents a number as a comparable string.
 * <br/>
 * Numbers are allowed in the range -500,000,000,000 to +500,000,000,000 (inclusive).
 * Comparisons are precise to 17 decimal places, with six to the right of the decimal.
 * Numbers are treated as floating-point values.
 * <br>
 * Numbers are converted to strings by:
 * 1. Multiplying by 1,000,000 to remove the decimal point and then adding 500,000,000,000 (to remove negatives), then
 * 2. Formatting to a 12-character to base64 string with padding, because the base64 string
 *     converted from 500,000,000,000 * 1,000,000 = 500,000,000,000,000,000 has 12 characters.
 * <br/>
 * Hexadecimal representation is used because:
 * 1. It saves 3 bytes of memory per number compared to decimal representation.
 * 2. It is lexicographically comparable, which is useful for maintaining sorted order of numbers.
 * 2. It aligns with the radix used for IP addresses.
 * <br/>
 * The number is parsed as a Java {@code BigDecimal} to support decimal fractions. We're avoiding double as
 * there is a well-known issue that double numbers can lose precision when performing calculations involving
 * other data types. The higher the number, the lower the accuracy that can be maintained. For example,
 * {@code 0.30d - 0.10d = 0.19999999999999998} instead of {@code 0.2d}. When extended to {@code 1e10}, the test
 * results show that only 5 decimal places of precision can be guaranteed when using doubles.
 * <br/>
 * CAVEAT:
 * The current range of +/- 500,000,000,000 is selected as a balance between maintaining the committed 6
 * decimal places of precision and memory cost (each number is parsed into a 12-character hexadecimal string).
 * When trying to increase the maximum number, PLEASE BE VERY CAREFUL TO PRESERVE THE NUMBER PRECISION AND
 * CONSIDER THE MEMORY COST.
 * <br/>
 * Also, while {@code BigDecimal} can ensure the precision of double calculations, it has been shown to be
 * 2-4 times slower for basic mathematical and comparison operations, so we turn to long integer arithmetic.
 * This will need to be modified if we ever need to support larger numbers.
 */
class ComparableNumber {
    // Use scientific notation to define the double number directly to avoid losing Precision by calculation
    // for example 5000 * 1000 *1000 will be wrongly parsed as 7.05032704E8 by computer.
    static final double HALF_TRILLION = 5E11;

    static final int MAX_LENGTH_IN_BYTES = 16;
    static final int MAX_DECIMAL_PRECISON = 6;

    public static final BigDecimal TEN_E_SIX = new BigDecimal("1E6"); // to remove decimals
    public static final long HALF_TRILLION_TEN_E_SIX = new BigDecimal(ComparableNumber.HALF_TRILLION).multiply(TEN_E_SIX).longValueExact();

    private ComparableNumber() {
    }

    /**
     * Generates a hexadecimal string representation of a given decimal string value,
     * with a maximum precision of 6 decimal places and a range between -5,000,000,000
     * and 5,000,000,000 (inclusive).
     *
     * @param str the decimal string value to be converted
     * @return the hexadecimal string representation of the input value
     * @throws IllegalArgumentException if the input value has more than 6 decimal places
     *                                  or is outside the allowed range
     */
    static String generate(final String str) {
        final BigDecimal number = JavaBigDecimalParser.parseBigDecimal(str).stripTrailingZeros();
        if (number.scale() > MAX_DECIMAL_PRECISON) {
            throw new IllegalArgumentException("Only values upto 6 decimals are supported");
        }

        final long shiftedBySixDecimals = number.multiply(TEN_E_SIX).longValueExact();

        // faster than doing bigDecimal comparisons
        if (shiftedBySixDecimals < -HALF_TRILLION_TEN_E_SIX || shiftedBySixDecimals > HALF_TRILLION_TEN_E_SIX) {
            throw new IllegalArgumentException("Value must be between " + -ComparableNumber.HALF_TRILLION +
                    " and " + ComparableNumber.HALF_TRILLION + ", inclusive");
        }

        return longToBase64Bytes(shiftedBySixDecimals + HALF_TRILLION_TEN_E_SIX);
    }

    public static String longToBase64Bytes(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Input value must be non-negative");
        }

        char[] bytes = new char[12]; // Maximum length of base-64 encoded long is 12 bytes
        int index = 11;

        while (value > 0) {
            int digit = (int) (value & 0x3F); // Get the lowest 6 bits
            bytes[index--] = (char) BASE64_DIGITS[digit];
            value >>= 6; // Shift the value right by 6 bits
        }

        while(index >= 0) { // left padding
            bytes[index--] = (char) MIN_NUM_DIGIT;
        }

        return new String(bytes);
    }

}

