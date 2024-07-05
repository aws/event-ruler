package software.amazon.event.ruler;

import com.fasterxml.jackson.core.io.doubleparser.JavaBigDecimalParser;
import com.fasterxml.jackson.core.io.doubleparser.JavaDoubleParser;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Represents a number as a comparable string.
 * <br/>
 * Numbers are allowed in the range -5,000,000,000 to +5,000,000,000 (inclusive).
 * Comparisons are precise to 6 decimal places.
 * Numbers are treated as floating-point values.
 * <br>
 * Numbers are converted to strings by:
 * 1. Multiplying by 1,000,000 to remove the decimal point and then adding 5,000,000,000 (to remove negatives), then m
 * 2. Formatting to a 14-character hexadecimal string left-padded with zeros, because the hexadecimal string
 *     converted from 5,000,000,000 * 1,000,000 = 5,000,000,000,000,000 has 14 characters.
 * <br/>
 * Hexadecimal representation is used because:
 * 1. It saves 3 bytes of memory per number compared to decimal representation.
 * 2. It aligns with the radix used for IP addresses.
 * If needed, a radix of 32 or 64 can be used to save more memory (e.g., the string length will be 10 for radix 32,
 * and 9 for radix 64).
 * <br/>
 * The number is parsed as a Java {@code BigDecimal} to support decimal fractions. We're avoiding double as
 * there is a well-known issue that double numbers can lose precision when performing calculations involving
 * other data types. The higher the number, the lower the accuracy that can be maintained. For example,
 * {@code 0.30d - 0.10d = 0.19999999999999998} instead of {@code 0.2d}. If extended to {@code 1e10}, the test
 * results show that only 5 decimal places of precision can be guaranteed when using doubles.
 * <br/>
 * CAVEAT:
 * The current maximum number of 5,000,000,000 is selected as a balance between maintaining the committed 6
 * decimal places of precision and memory cost (each number is parsed into a 14-character hexadecimal string).
 * When trying to increase the maximum number, PLEASE BE VERY CAREFUL TO PRESERVE THE NUMBER PRECISION AND
 * CONSIDER THE MEMORY COST.
 * <br/>
 * Also, while {@code BigDecimal} can ensure the precision of double
 * calculations, it has been shown to be 2-4 times slower for basic mathematical and comparison operations, so.
 * we turn to long integer arithmetic.
 */
class ComparableNumber {
    static final int MAX_LENGTH_IN_BYTES = 16;
    static final int MAX_DECIMAL_PRECISON = 6;

    public static final BigDecimal TEN_E_SIX = new BigDecimal("1E6");
    public static final long FIV_BILL_TEN_E_SIX = new BigDecimal(Constants.FIVE_BILLION).multiply(TEN_E_SIX).longValueExact();

    private static final String HEXES = new String(Constants.HEX_DIGITS, StandardCharsets.US_ASCII);
    public static final int NIBBLE_SIZE = 4;
    private static final int UPPER_NIBBLE_MASK = 0xF0; // 1111 0000
    private static final int LOWER_NIBBLE_MASK = 0x0F; // 0000 1111

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
        final BigDecimal number = getNumber(str).stripTrailingZeros();
        if (number.scale() > MAX_DECIMAL_PRECISON) {
            throw new IllegalArgumentException("Only values upto 6 decimals are supported" + number + " " + str + " " + Double.toHexString(number.setScale(6, RoundingMode.HALF_UP).doubleValue()));
        }

        final long shiftedBySixDecimals = number.multiply(TEN_E_SIX).longValueExact();

        // faster than doing bigDecimal comparisons
        if (shiftedBySixDecimals < -FIV_BILL_TEN_E_SIX || shiftedBySixDecimals > FIV_BILL_TEN_E_SIX) {
            throw new IllegalArgumentException("Value must be between " + -Constants.FIVE_BILLION +
                    " and " + Constants.FIVE_BILLION + ", inclusive");
        }

        return toHexStringSkippingFirstByte(shiftedBySixDecimals + FIV_BILL_TEN_E_SIX);
    }

    private static BigDecimal getNumber(String str) {
        try {
            return JavaBigDecimalParser.parseBigDecimal(str);
        } catch (NumberFormatException e) {
            // maybe it is a hex, fall back to using double where precision isn't guaranteed
            // we keep existing behaviour of ignore after 6 decimal to avoid breaking backward compatibility
            // as an acceptable trade-off https://github.com/aws/event-ruler/issues/163
            return new BigDecimal(Double.parseDouble(str)).setScale(MAX_DECIMAL_PRECISON, RoundingMode.DOWN);
        }
    }

    /**
     * converts a single byte to its two hexadecimal character representation
     * @param value the byte we want to convert to hex string
     * @return a 2 digit char array with the equivalent hex representation
     */
    static char[] byteToHexChars(byte value) {

        char[] result = new char[2];

        int upperNibbleIndex = (value & UPPER_NIBBLE_MASK) >> NIBBLE_SIZE;
        int lowerNibbleIndex = value & LOWER_NIBBLE_MASK;

        result[0] = HEXES.charAt(upperNibbleIndex);
        result[1] = HEXES.charAt(lowerNibbleIndex);

        return result;

    }

    private static byte[] longToByteBuffer(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        return buffer.array();
    }

    static String toHexStringSkippingFirstByte(long value) {
        byte[] raw = longToByteBuffer(value);
        char[] outputChars = new char[14];
        for (int i = 1; i < raw.length; i++) {
            int pos = (i - 1) * 2;
            char[] currentByteChars = byteToHexChars(raw[i]);
            outputChars[pos] = currentByteChars[0];
            outputChars[pos + 1] = currentByteChars[1];
        }
        return new String(outputChars);
    }

}

