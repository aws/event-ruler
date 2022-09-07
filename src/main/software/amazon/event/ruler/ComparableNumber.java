package software.amazon.event.ruler;

import java.nio.ByteBuffer;

/**
 * Represents a number, turned into a comparable string
 *  Numbers are allowed in the range -50**9 .. +50**9, inclusive
 *  Comparisons are precise to 6 digits to the right of the decimal point
 *  They are all treated as floating point
 *  They are turned into strings by:
 *  1. Add 10**9 (so no negatives), then multiply by 10**6 to remove the decimal point
 *  2. Format to a 14 char string left padded with 0 because hex string converted from 5e9*1e6=10e15 has 14 characters.
 *  Note: We use Hex because of a) it can save 3 bytes memory per number than decimal b) it aligned IP address radix.
 *  If needed, we can consider to use 32 or 64 radix description to save more memory,e.g. the string length will be 10
 *  for 32 radix, and 9 for 64 radix.
 *
 *  Note:
 *  The number is parsed to be java double to support number with decimal fraction, the max range supported is from
 *  -5e9 to 5e9 with precision of 6 digits to the right of decimal point.
 *  There is well known issue that double number will lose the precision while calculation among double and other data
 *  types, the higher the number, the lower the accuracy that can be maintained.
 *  For example: 0.30d - 0.10d = 0.19999999999999998 instead of 0.2d, and if extend to 1e10, the test result shows only
 *  5 digits of precision from right of decimal point can be guaranteed with existing implementation.
 *  The current max number 5e9 is selected with a balance between keeping the committed 6 digits of precision from right
 *  of decimal point and the memory cost (each number is parsed into a 14 characters HEX string).
 *
 *  CAVEAT:
 *  When there is need to further enlarging the max number, PLEASE BE VERY CAREFUL TO RESERVE THE NUMBER PRECISION AND
 *  TAKEN THE MEMORY COST INTO CONSIDERATION, BigDecimal shall be used to ensure the precision of double calculation ...
 */
class ComparableNumber {
    private static final double TEN_E_SIX = 1E6;
    static final int MAX_LENGTH_IN_BYTES = 16;

    private ComparableNumber() { }

    static String generate(final double f) {
        if (f < -Constants.FIVE_BILLION || f > Constants.FIVE_BILLION) {
            throw new IllegalArgumentException("Value must be between " + -Constants.FIVE_BILLION +
                    " and " + Constants.FIVE_BILLION + ", inclusive");
        }

        return toHexStringSkippingFirstByte((long) (TEN_E_SIX * (Constants.FIVE_BILLION + f)));
    }

    private static final String HEXES = "0123456789ABCDEF";

    private static byte[] longToByteBuffer(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        return buffer.array();
    }

    static String toHexStringSkippingFirstByte(Long value) {
        byte[] raw = longToByteBuffer(value);
        char[] chars = new char[14];
        for (int i = 1; i < raw.length; i++) {
            byte b = raw[i];
            int pos = (i - 1) * 2;
            chars[pos] = HEXES.charAt((b & 0xF0) >> 4);
            chars[pos + 1] = HEXES.charAt((b & 0x0F));
        }
        return new String(chars);
    }

}

