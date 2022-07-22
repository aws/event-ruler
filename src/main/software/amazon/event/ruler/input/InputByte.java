package software.amazon.event.ruler.input;

import java.nio.charset.StandardCharsets;

import static software.amazon.event.ruler.input.InputCharacterType.BYTE;

/**
 * An InputCharacter that represents a single byte.
 */
public class InputByte extends InputCharacter {

    private final byte b;

    InputByte(final byte b) {
        this.b = b;
    }

    public static InputByte cast(InputCharacter character) {
        return (InputByte) character;
    }

    public byte getByte() {
        return b;
    }

    @Override
    public InputCharacterType getType() {
        return BYTE;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o.getClass() == getClass())) {
            return false;
        }
        return ((InputByte) o).getByte() == getByte();
    }

    @Override
    public int hashCode() {
        return Byte.valueOf(b).hashCode();
    }

    @Override
    public String toString() {
        return new String(new byte[] { getByte() }, StandardCharsets.UTF_8);
    }
}
