package software.amazon.event.ruler;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents a range of numeric values to match against.
 * "Numeric" means that the character repertoire is "digits"; initially, either 0-9 or 0-9a-f. In the current
 *  implementation, the number of digits in the top and bottom of the range is the same.
 */
public class Range extends Patterns {
    /**
     * Bottom and top of the range. openBottom true means we're looking for > bottom, false means >=
     *  Similarly, openTop true means we're looking for < top, false means <= top.
     */
    final byte[] bottom;
    final boolean openBottom;
    final byte[] top;
    final boolean openTop;

    final boolean isCIDR;

    private static final int HEX_DIGIT_A_DECIMAL_VALUE = 10;

    private Range(final double bottom, final boolean openBottom, final double top, final boolean openTop) {
        super(MatchType.NUMERIC_RANGE);
        if (bottom >= top) {
            throw new IllegalArgumentException("Bottom must be less than top");
        }
        this.bottom = ComparableNumber.generate(bottom).getBytes(StandardCharsets.UTF_8);
        this.openBottom = openBottom;
        this.top = ComparableNumber.generate(top).getBytes(StandardCharsets.UTF_8);
        this.openTop = openTop;
        isCIDR = false;
    }

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

    public static Range lessThan(final double val) {
        return new Range(-Constants.FIVE_BILLION, false, val, true);
    }

    public static Range lessThanOrEqualTo(final double val) {
        return new Range(-Constants.FIVE_BILLION, false, val, false);
    }

    public static Range greaterThan(final double val) {
        return new Range(val, true, Constants.FIVE_BILLION, false);
    }

    public static Range greaterThanOrEqualTo(final double val) {
        return new Range(val, false, Constants.FIVE_BILLION, false);
    }

    public static Range between(final double bottom, final boolean openBottom, final double top, final boolean openTop) {
        return new Range(bottom, openBottom, top, openTop);
    }

    private static Range deepCopy(final Range range) {
        return new Range(range);
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
    static byte[] digitSequence(byte first, byte last, boolean includeFirst, boolean includeLast) {
        assert first <= last && first <= 'F' && first >= '0' && last <= 'F';
        assert !((first == last) && !includeFirst && !includeLast);

        int i = getHexByteIndex(first);
        int j = getHexByteIndex(last);

        if ((!includeFirst) && (i < (Constants.HEX_DIGITS.length - 1))) {
            i++;
        }

        if (includeLast) {
            j++;
        }

        byte[] bytes = new byte[j - i];

        System.arraycopy(Constants.HEX_DIGITS, i, bytes, 0, j - i);

        return bytes;
    }

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
        return (new String(bottom, StandardCharsets.UTF_8)) + '/' + (new String(top, StandardCharsets.UTF_8))
                       + ':' + openBottom + '/' + openTop + " (" + super.toString() + ")";
    }
}
