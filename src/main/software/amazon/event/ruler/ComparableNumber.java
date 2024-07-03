package software.amazon.event.ruler;

import com.fasterxml.jackson.core.io.doubleparser.JavaDoubleParser;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Represents a number, turned into a comparable string
 *  Numbers are allowed in the range -50**9 .. +50**9, inclusive
 *  Comparisons are precise to 6 digits to the right of the decimal point
 *  They are all treated as floating point
 *  They are turned into strings by:
 *  1. Add 10**9 (so no negatives), then multiply by 10**6 to remove the decimal point
 *  2. Format to a 14 char string left padded with 0 because hex string converted from 5e9*1e6=10e15 has 14 characters.
 *  <br>
 *  We are using Hex because a) it saves 3 bytes memory per number than decimal b) it is aligned with IP address radix.
 *  If needed, we can use 32 or 64 radix description to save more memory, e.g. the string length will be 10
 *  for 32 radix, and 9 for 64 radix.
 *  <br>
 *  The number is parsed to be java double to support number with decimal fraction, the max range supported is from
 *  -5e9 to 5e9 with precision of 6 digits to the right of decimal point.
 *  There is well known issue that double number will lose the precision while calculation among double and other data
 *  types, the higher the number, the lower the accuracy that can be maintained.
 *  For example: 0.30d - 0.10d = 0.19999999999999998 instead of 0.2d, and if extend to 1e10, the test result shows only
 *  5 digits of precision from right of decimal point can be guaranteed with existing implementation.
 *  The current max number 5e9 is selected with a balance between keeping the committed 6 digits of precision from right
 *  of decimal point and the memory cost (each number is parsed into a 14 characters HEX string).
 *  <br>
 *  CAVEAT:
 *  When there is need to further enlarging the max number, PLEASE BE VERY CAREFUL TO RESERVE THE NUMBER PRECISION AND
 *  TAKEN THE MEMORY COST INTO CONSIDERATION. Also, while BigDecimal can ensure the precision of double calculation, it
 *  has shown to be 2~4x slower.
 */
class ComparableNumber {
    private static final double TEN_E_SIX = 1E6;
    static final int MAX_LENGTH_IN_BYTES = 16;
    private static final String HEXES = new String(Constants.HEX_DIGITS, StandardCharsets.US_ASCII);
    public static final int NIBBLE_SIZE = 4;

    // 1111 0000
    private static final int UPPER_NIBBLE_MASK = 0xF0;

    // 0000 1111
    private static final int LOWER_NIBBLE_MASK = 0x0F;

    private ComparableNumber() {
    }


    static String generate(final String str) {
        if (getPrecision(str) > 6) {
            throw new IllegalArgumentException("Only values upto 6 decimals are supported");
        }

        return generate(JavaDoubleParser.parseDouble(str));
    }

    static long getPrecision(String str) {
        int exponentialAt = 0;
        int decimalAt = 0;
        long trailingZeros = 0;
        final int length = str.length();
        for(int i = 0; i < length; i++) { // check for hexes
            char c = str.charAt(i);
            if (c == 'I' ||c == 'N' ) {
                return 0; // no decimals for Infinity or NaN
            }
            if(c == 'x' || c == 'X') {
                return 0; // FIXME Hex Exponents need more work (0x12.0P2)
            }

            if (c == '.') {
                decimalAt = length - i - 1;
            } else if ((c == 'e' || c == 'E') && i < length - 1) {
                exponentialAt = i + 1;
            }

            if(decimalAt > 0 && exponentialAt == 0) {
                if(c == '0') {
                    trailingZeros += 1;
                } else {
                    trailingZeros = 0;
                }
            }
        }

        long precision = decimalAt;
        final long exponent;
        final long preDecimal;
        final long postDecimal;

        if(trailingZeros > 0) {
            precision -= trailingZeros;
        }

        if (decimalAt > 0 && exponentialAt != 0) {
            exponent = Long.parseLong(str.substring(exponentialAt)); // use math here.
            precision -= exponent; // move decimal by exponent value
            precision -= length - exponentialAt + 1; // remove the exponents from the count as well
        }

        return Math.max(0, precision);
    }

    static String generate(final double d) {
        if (d < -Constants.FIVE_BILLION || d > Constants.FIVE_BILLION) {
            throw new IllegalArgumentException("Value must be between " + -Constants.FIVE_BILLION +
                    " and " + Constants.FIVE_BILLION + ", inclusive");
        }

        return toHexStringSkippingFirstByte(Math.round(TEN_E_SIX * Constants.FIVE_BILLION) + Math.round(TEN_E_SIX * d));
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

