package software.amazon.event.ruler.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static software.amazon.event.ruler.input.Parser.NINE_BYTE;
import static software.amazon.event.ruler.input.Parser.ZERO_BYTE;

/**
 * A grouping of multiple bytes. This can be used to represent a character that has a UTF-8 representation requiring
 * multiple bytes.
 */
public class MultiByte {

    private final byte[] bytes;

    MultiByte(byte ... bytes) {
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Must provide at least one byte");
        }
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public byte singular() {
        if (bytes.length != 1) {
            throw new IllegalStateException("Must be a singular byte");
        }
        return bytes[0];
    }

    public boolean is(byte ... bytes) {
        return Arrays.equals(this.bytes, bytes);
    }

    public boolean isNumeric() {
        return bytes.length == 1 && bytes[0] >= ZERO_BYTE && bytes[0] <= NINE_BYTE;
    }

    public MultiByte createNext() {
        if (is((byte) 0x7F)) {
            return new MultiByte((byte) 0xC2, (byte) 0x80);
        } else if (is((byte) 0xC2, (byte) 0xBF)) {
            return new MultiByte((byte) 0XC3, (byte) 0x80);
        } else if (is((byte) 0xC3, (byte) 0xBF)) {
            return new MultiByte((byte) 0x00);
        } else {
            byte[] copy = Arrays.copyOf(bytes, bytes.length);
            copy[copy.length - 1]++;
            return new MultiByte(copy);
        }
    }

    public boolean isLessThan(MultiByte other) {
        return isLessThan(other, false);
    }

    public boolean isLessThanOrEqualTo(MultiByte other) {
        return isLessThan(other, true);
    }

    public boolean isGreaterThan(MultiByte other) {
        return !isLessThanOrEqualTo(other);
    }

    public boolean isGreaterThanOrEqualTo(MultiByte other) {
        return !isLessThan(other);
    }

    private boolean isLessThan(MultiByte other, boolean orEqualTo) {
        byte[] otherBytes = other.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            if (i == otherBytes.length) {
                // otherBytes is a prefix of bytes. Thus, bytes is larger than otherBytes.
                return false;
            }
            if (bytes[i] != otherBytes[i]) {
                // Most significant differing byte - return if it is less for bytes than otherBytes.
                return (0xFF & bytes[i]) < (0xFF & otherBytes[i]);
            }
        }
        // bytes is equal to (return true if orEqualTo) or a prefix of (return true) otherBytes.
        return orEqualTo || (0xFF & bytes.length) < (0xFF & otherBytes.length);
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || !(o.getClass() == getClass())) {
            return false;
        }

        return Arrays.equals(((MultiByte) o).getBytes(), getBytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getBytes());
    }

    @Override
    public String toString() {
        return Arrays.toString(getBytes());
    }
}
