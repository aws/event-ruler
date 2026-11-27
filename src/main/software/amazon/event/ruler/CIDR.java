package software.amazon.event.ruler;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static software.amazon.event.ruler.Constants.HEX_DIGITS;

/**
 * Supports matching on IPv4 and IPv6 CIDR patterns, as compressed into a string range match.
 */
public class CIDR {

    private final static byte[] LEADING_MIN_BITS = { (byte) 0xff, (byte) 0xfe, (byte) 0xfc, (byte) 0xf8, (byte) 0xf0, (byte) 0xe0, (byte) 0xc0, (byte) 0x80, 0x00 };

    private final static byte[] TRAILING_MAX_BITS = { 0x0, 0x01, 0x03, 0x07, 0x0f, 0x1f, 0x3f, 0x7f, (byte) 0xff };

    private CIDR() { }

    private static boolean isIPv4OrIPv6(String ip) {
        return Constants.IPv4_REGEX.matcher(ip).matches() || Constants.IPv6_REGEX.matcher(ip).matches();
    }

    private static byte[] ipToBytes(final String ip) {

        // have to do the regex check because if we past what looks like a hostname, InetAddress.getByName will
        //  launch a DNS search
        byte[] addr = {};
        try {
            if (isIPv4OrIPv6(ip)) {
                addr = InetAddress.getByName(ip).getAddress();
            }
        } catch (Exception e) {
            barf("Invalid IP address: " + ip);
        }
        if (addr.length != 4 && addr.length != 16) {
            barf("Nonstandard IP address: " + ip);
        }
        return addr;
    }

    /**
     * Converts an IP address literal (v4 or v6) into a hexadecimal string.
     *  Throws an IllegalArgumentException if the alleged ip is not a valid IP address literal.
     * @param ip String alleged to be an IPv4 or IPv6 address literal.
     * @return Hexadecimal form of the address, 4 or 16 bytes.
     */
    static String ipToString(final String ip) {
        return new String(toHexDigits(ipToBytes(ip)), StandardCharsets.UTF_8);
    }

    /**
     * Converts an IP address literal into a hexadecimal string if this is possible.  If not
     *  possible, returns the original string.
     * @param ip String that might be an IP address literal
     * @return Hexadecimal form of the input, or the original string.
     */
    static String ipToStringIfPossible(final String ip) {
        if (!isIPv4OrIPv6(ip)) {
            return ip;
        }
        try {
            return ipToString(ip);
        } catch (Exception e){
            return ip;
        }
    }

    /**
     * Converts a string to an IP address literal to isCIDR format Range if this is possible.
     * If not possible, returns null.
     * @param ip String that might be an IP address literal
     * @return Range with isCIDR as true
     */
    static Range ipToRangeIfPossible(final String ip) {
        if (!isIPv4OrIPv6(ip)) {
            return null;
        }
        try {
            final byte[] digits = toHexDigits(ipToBytes(ip));
            final byte[] bottom = digits.clone();
            final byte[] top = digits.clone();
            boolean openBottom;
            boolean openTop;
            byte lastByte = top[top.length - 1];

            if (lastByte == Constants.MAX_HEX_DIGIT) {
                bottom[top.length - 1] = (byte) (lastByte - 1);
                openBottom = true;
                openTop = false;
            } else {
                if (lastByte != HEX_DIGITS[9]) {
                    top[top.length - 1] = (byte) (lastByte + 1);
                } else {
                    top[top.length - 1] = HEX_DIGITS[10];
                }
                openBottom = false;
                openTop = true;
            }
            return new Range(bottom, openBottom, top, openTop, true);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] toHexDigits(final byte[] address) {

        final byte[] digits = new byte[address.length * 2];
        int digitInd = 0;
        for (int ipByte : address) {
            digits[digitInd++] = HEX_DIGITS[(ipByte >> 4) & 0x0F];
            digits[digitInd++] = HEX_DIGITS[ipByte & 0x0F];
        }
        return digits;
    }

    public static Range cidr(String cidr) throws IllegalArgumentException {

        String[] slashed = cidr.split("/");
        if (slashed.length != 2) {
            barf("Malformed CIDR, one '/' required");
        }
        int maskBits = -1;
        try {
            maskBits = Integer.parseInt(slashed[1]);
        } catch (NumberFormatException e) {
            barf("Malformed CIDR, mask bits must be an integer");
        }
        if (maskBits < 0) {
            barf("Malformed CIDR, mask bits must not be negative");
        }

        byte[] providedIp = ipToBytes(slashed[0]);
        if (providedIp.length == 4) {
            if (maskBits > 31) {
                barf("IPv4 mask bits must be < 32");
            }
        } else {
            if (maskBits > 127) {
                barf("IPv6 mask bits must be < 128");
            }
        }

        byte[] minBytes = computeBottomBytes(providedIp, maskBits);
        byte[] maxBytes = computeTopBytes(providedIp, maskBits);
        return new Range(toHexDigits(minBytes), false, toHexDigits(maxBytes), false, true);
    }

    /**
     * Calculate the byte representation of the lowest IP address covered by the provided CIDR.
     *
     * @param baseBytes The byte representation of the IP address (left-of-slash) component of the provided CIDR.
     * @param maskBits The integer (right-of-slash) of the provided CIDR.
     * @return The byte representation of the lowest IP address covered by the provided CIDR.
     */
    private static byte[] computeBottomBytes(final byte[] baseBytes, final int maskBits) {

        int variableBits = computeVariableBits(baseBytes, maskBits);

        byte[] minBytes = new byte[baseBytes.length];
        for (int i = baseBytes.length - 1; i >= 0; i--) {

            if (variableBits > 0) {
                minBytes[i] = (byte) (baseBytes[i] & LEADING_MIN_BITS[Math.min(8, variableBits)]);
            } else {
                minBytes[i] = baseBytes[i];
            }

            variableBits -= 8;
        }

        return minBytes;
    }

    /**
     * Calculate the byte representation of the highest IP address covered by the provided CIDR.
     *
     * @param baseBytes The byte representation of the IP address (left-of-slash) component of the provided CIDR.
     * @param maskBits The integer (right-of-slash) of the provided CIDR.
     * @return The byte representation of the highest IP address covered by the provided CIDR.
     */
    private static byte[] computeTopBytes(final byte[] baseBytes, final int maskBits) {

        int variableBits = computeVariableBits(baseBytes, maskBits);

        byte[] maxBytes = new byte[baseBytes.length];
        for (int i = baseBytes.length - 1; i >= 0; i--) {

            if (variableBits > 0) {
                maxBytes[i] = (byte) (baseBytes[i] | TRAILING_MAX_BITS[Math.min(8, variableBits)]);
            } else {
                maxBytes[i] = baseBytes[i];
            }

            variableBits -= 8;
        }

        return maxBytes;
    }

    /**
     * The maskBits in a provided CIDR refer to the number of leading bits in the binary representation of the IP
     * address component that are fixed. Thus, variableBits refers to the number of remaining (trailing) bits.
     *
     * @param baseBytes The byte representation of the IP address (left-of-slash) component of the provided CIDR.
     * @param maskBits The integer (right-of-slash) of the provided CIDR.
     * @return The number of variable (trailing) bits after the fixed maskBits bits.
     */
    private static int computeVariableBits(final byte[] baseBytes, final int maskBits) {
        return (baseBytes.length == 4 ? 32 : 128) - maskBits;
    }

    private static void barf(final String msg) throws IllegalArgumentException {
        throw new IllegalArgumentException(msg);
    }

}
