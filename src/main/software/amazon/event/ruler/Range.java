package software.amazon.event.ruler;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;

import static software.amazon.event.ruler.Constants.BASE128_DIGITS;
import static software.amazon.event.ruler.Constants.HEX_DIGITS;
import static software.amazon.event.ruler.Constants.MAX_HEX_DIGIT;
import static software.amazon.event.ruler.Constants.MAX_NUM_DIGIT;
import static software.amazon.event.ruler.Constants.MIN_HEX_DIGIT;
import static software.amazon.event.ruler.Constants.MIN_NUM_DIGIT;

/**
 * Represents a range of numeric values to match against.
 * "Numeric" means that the character repertoire is "digits"; initially, either 0-9 or 0-9a-f. In the current
 *  implementation, the number of digits in the top and bottom of the range is the same.
 */
public final class Range extends Patterns {
    private static final byte[] MIN_RANGE_BYTES = doubleToComparableBytes(-Double.MAX_VALUE);
    private static final byte[] MAX_RANGE_BYTES = doubleToComparableBytes(Double.MAX_VALUE);
    private static final int HEX_DIGIT_A_DECIMAL_VALUE = 10;
    /**
     * Bottom and top of the range. openBottom true means we're looking for > bottom, false means >=
     *  Similarly, openTop true means we're looking for < top, false means <= top.
     */
    final byte[] bottom;
    final boolean openBottom;
    final byte[] top;
    final boolean openTop;

    final boolean isCIDR;

    Range(final byte[] bottom, final boolean openBottom, final byte[] top, final boolean openTop, final boolean isCIDR) {
        super(MatchType.NUMERIC_RANGE);
        this.bottom = bottom;
        this.top = top;
        this.openBottom = openBottom;
        this.openTop = openTop;
        this.isCIDR = isCIDR;
    }

    private Range(Range range) {
        super(MatchType.NUMERIC_RANGE);
        this.bottom = range.bottom.clone();
        this.openBottom = range.openBottom;
        this.top = range.top.clone();
        this.openTop = range.openTop;
        this.isCIDR = range.isCIDR;
    }

    public static Range lessThan(final String val) {
        byte[] byteVal = stringToComparableBytes(val);
        return between(MIN_RANGE_BYTES, false, byteVal, true);
    }

    public static Range lessThanOrEqualTo(final String val) {
        byte[] byteVal = stringToComparableBytes(val);
        return between(MIN_RANGE_BYTES, false, byteVal, false);
    }

    public static Range greaterThan(final String val) {
        byte[] byteVal = stringToComparableBytes(val);
        return between(byteVal, true, MAX_RANGE_BYTES, false);
    }

    public static Range greaterThanOrEqualTo(final String val) {
        byte[] byteVal = stringToComparableBytes(val);
        return between(byteVal, false, MAX_RANGE_BYTES, false);
    }

    public static Range between(final String bottom, final boolean openBottom, final String top, final boolean openTop) {
        byte[] byteBottom = stringToComparableBytes(bottom);
        byte[] byteTops = stringToComparableBytes(top);
        ensureValidRange(byteBottom, byteTops);
        return new Range(byteBottom, openBottom, byteTops, openTop, false);
    }

    public static Range between(final byte[] bottom, final boolean openBottom, final byte[] top, final boolean openTop) {
        return new Range(bottom, openBottom, top, openTop, false);
    }

    private static Range deepCopy(final Range range) {
        return new Range(range);
    }

    /**
     * Ensures that the given byte arrays representing the bottom and top of a range
     * are valid, where the bottom array's hex representation is less than the top array.
     *
     * @param byteBottom the byte array representing the bottom of the range
     * @param byteTops   the byte array representing the top of the range
     * @throws IllegalArgumentException if the bottom array is not lexicographically less than the top array
     */
    private static void ensureValidRange(byte[] byteBottom, byte[] byteTops) {
        int equals = 0;
        for(int i = 0; i < byteBottom.length; i++) {
            if(byteBottom[i] == byteTops[i]) {
                equals += 1;
            } else if(byteBottom[i] > byteTops[i]) {
                throw new IllegalArgumentException("Bottom must be less than top");
            } else {
                break; // found one to be bigger than the other
            }
        }
        if(equals == byteBottom.length) {
            throw new IllegalArgumentException("Bottom must be less than top");
        }
    }

    public byte maxDigit() {
        return isCIDR ? MAX_HEX_DIGIT : MAX_NUM_DIGIT;
    }

    public byte minDigit() {
        return isCIDR ? MIN_HEX_DIGIT : MIN_NUM_DIGIT;
    }

    /**
     * This is necessitated by the fact that we do range comparisons of numbers, fixed-length strings of digits, and
     *  in the case where the numbers represent IP addresses, they are hex digits.  So we need to be able to say
     *  "for all digits between '3' and 'C'". This is for that.
     *
     * @param first Start one digit higher than this, for example '4'
     * @param last Stop one digit lower than this, for example 'B'
     * @return The digit list, for example [ '4', '5', '6', '7', '8', '9', '9', 'A' ] (with 'B' for longDigitSequence)
     */
    static byte[] digitSequence(byte first, byte last, boolean includeFirst, boolean includeLast, boolean isCIDR) {
        return isCIDR ?
                digitSequence(first, last, includeFirst, includeLast, HEX_DIGITS, Range::getHexByteIndex) :
                digitSequence(first, last, includeFirst, includeLast, BASE128_DIGITS, Integer::new);
    }

    private static byte[] digitSequence(byte first, byte last, boolean includeFirst, boolean includeLast,
                                        byte[] digits, Function<Byte, Integer> byteIndexFunc) {
        final byte maxPossibleDigit = digits[digits.length - 1];
        final byte minPossibleDigit = digits[0];

        assert first <= last && first <= maxPossibleDigit && first >= minPossibleDigit && last <= maxPossibleDigit;
        assert !((first == last) && !includeFirst && !includeLast);

        int i = byteIndexFunc.apply(first);
        int j = byteIndexFunc.apply(last);

        if ((!includeFirst) && (i < (digits.length - 1))) {
            i++;
        }

        if (includeLast) {
            j++;
        }

        byte[] bytes = new byte[j - i];

        System.arraycopy(digits, i, bytes, 0, j - i);

        return bytes;
    }

    // quickly find the index of chars within Constants.HEX_DIGITS
    private static int getHexByteIndex(byte value) {
        // ['0'-'9'] maps to [0-9] indexes
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        // ['A'-'F'] maps to [10-15] indexes
        return (value - 'A') + HEX_DIGIT_A_DECIMAL_VALUE;
    }

    @Override
    public Object clone() {
        super.clone();
        return Range.deepCopy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !o.getClass().equals(getClass())) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        Range range = (Range) o;

        return openBottom == range.openBottom &&
               openTop == range.openTop &&
               Arrays.equals(bottom, range.bottom) &&
               Arrays.equals(top, range.top);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(bottom);
        result = 31 * result + Boolean.hashCode(openBottom);
        result = 31 * result + Arrays.hashCode(top);
        result = 31 * result + Boolean.hashCode(openTop);
        return result;
    }

    public String toString() {
        if(isCIDR) {
            return (new String(bottom, StandardCharsets.UTF_8)) + '/' + (new String(top, StandardCharsets.UTF_8))
                    + ':' + openBottom + '/' + openTop + ':' + isCIDR + " (" + super.toString() + ")";
        } else {
            return "" +
                    ComparableNumber.toIntVals(new String(bottom, StandardCharsets.UTF_8)) + '/' +
                    ComparableNumber.toIntVals(new String(top, StandardCharsets.UTF_8))
                    + ':' + openBottom + '/' + openTop + ':' + isCIDR + " (" + super.toString() + ")";
        }
    }

    private static byte[] doubleToComparableBytes(double d) {
        return stringToComparableBytes(Double.toString(d));
    }

    private static byte[] stringToComparableBytes(String string) {
        return ComparableNumber.generate(string).getBytes(StandardCharsets.UTF_8);
    }
}
