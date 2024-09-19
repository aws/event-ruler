package software.amazon.event.ruler;

import ch.randelshofer.fastdoubleparser.JavaBigDecimalParser;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a number as a comparable string.
 * <br/>
 * All possible double numbers (IEEE-754 binary64 standard) are allowed.
 * Numbers are first standardized to floating-point values and then converted
 * to a Base128 encoded string of 10 bytes.
 * <br/>
 * We use Base128 encoding offers a compact representation of decimal numbers
 * as it preserves the lexicographical order of the numbers. See
 * https://github.com/aws/event-ruler/issues/179 for more context.
 * <br/>
 * The numbers are first parsed as a Java {@code BigDecimal} as there is a well known issue
 * where parsing directly to {@code Double} can lose precision when parsing doubles. It's
 * probably possible to support wider ranges with our current implementation of parsing strings to
 * BigDecimal, but it's not worth the effort as JSON also support upto float64 range. In
 * case this requirement changes, it would be advisable to move away from using {@code Doubles}
 * and {@code Long} in this class.
 * <br/>
 * CAVEAT:
 * There are precision and memory implications of the implementation here.
 * When trying to increase the maximum number, PLEASE BE VERY CAREFUL TO PRESERVE THE NUMBER PRECISION AND
 * CONSIDER THE MEMORY COST.
 */
class ComparableNumber {

    static final int MAX_LENGTH_IN_BYTES = 10;
    static final int BASE_128_BITMASK = 0x7f; // 127 or 01111111

    private ComparableNumber() {}

    /**
     * Generates a comparable number string from a given string representation
     * using numbits representation.
     *
     * @param str the string representation of the number
     * @return the comparable number string
     * @throws NumberFormatException if the input isn't a number
     * @throws IllegalArgumentException if the input isn't a number we can compare
     */
    static String generate(final String str) {
        final BigDecimal bigDecimal = JavaBigDecimalParser.parseBigDecimal(str);
        final double doubleValue = bigDecimal.doubleValue();

        // make sure we have the comparable numbers and haven't eaten up decimals values
        if(Double.isNaN(doubleValue) || Double.isInfinite(doubleValue) ||
                BigDecimal.valueOf(doubleValue).compareTo(bigDecimal) != 0) {
            throw new IllegalArgumentException("Cannot compare number : " + str);
        }
        final long bits = Double.doubleToRawLongBits(doubleValue);

        // if high bit is 0, we want to xor with sign bit 1 << 63, else negate (xor with ^0). Meaning,
        // bits >= 0, mask = 1000000000000000000000000000000000000000000000000000000000000000
        // bits < 0,  mask = 1111111111111111111111111111111111111111111111111111111111111111
        final  long mask = ((bits >>> 63) * 0xFFFFFFFFFFFFFFFFL) | (1L  << 63);
        return numbits(bits ^ mask );
    }

    /**
     * Converts a long value to a Base128 encoded string representation.
     * <br/>
     * The Base128 encoding scheme is a way to represent a long value as a sequence
     * of bytes, where each byte encodes 7 bits of the original value. This allows for
     * efficient storage and transmission of large numbers.
     * <br/>
     * The method first determines the number of trailing zero bytes in the input
     * value by iterating over the bytes from the most significant byte to the least
     * significant byte, and counting the number of consecutive zero bytes at the end.
     * It then creates a byte array of fixed length {@code MAX_LENGTH_IN_BYTES} and
     * populates it with the Base128 encoded bytes of the input value, starting from
     * the least significant byte.
     * <br/>
     * As shown in Quamina's numbits.go, it's possible to use variable length encoding
     * to reduce storage for simple (often common) numbers but it's not done here to
     * keep range comparisons simple for now.
     *
     * @param value the long value to be converted
     * @return the Base128 encoded string representation of the input value
     */
    public static String numbits(long value) {
        int trailingZeroes = 0;
        int index;
        // Count the number of trailing zero bytes to skip setting them
        for(index = MAX_LENGTH_IN_BYTES - 1; index >= 0; index--) {
            if((value & BASE_128_BITMASK) != 0) {
                break;
            }
            trailingZeroes ++;
            value >>= 7;
        }

        byte[] result = new byte[MAX_LENGTH_IN_BYTES];

        // Populate the byte array with the Base128 encoded bytes of the input value
        for(; index >= 0; index--) {
            result[index] = (byte)(value & BASE_128_BITMASK);
            value >>= 7;
        }

        return new String(result, StandardCharsets.UTF_8);
    }

    /**
     * This is a utility function for debugging and tests.
     * Converts a given string into a list of integers, where each integer represents
     * the ASCII value of the corresponding character in the string.
     */
    static List<Integer> toIntVals(String s) {
        Integer[] arr = new Integer[s.length()];
        for (int i=0; i<s.length(); i++) {
            arr[i] = (int)s.charAt(i);
        }
        return Arrays.asList(arr);
    }
}

