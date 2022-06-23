package software.amazon.event.ruler;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static software.amazon.event.ruler.Constants.HEX_DIGITS;
import static software.amazon.event.ruler.Constants.MAX_DIGIT;

/**
 * Supports matching on IPv4 and IPv6 CIDR patterns, as compressed into a string range match.
 */
public class CIDR {

    private final static byte[] TRAILING_MAX_BITS = { 0x0, 0x01, 0x03, 0x07, 0x0f, 0x1f, 0x3f, 0x7f };

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
     * Converts a string to an IP address literal if this is possible.  If not
     *  possible, returns the original string.
     * @param ip String that might be an IP address literal
     * @return Hexadecimal form of the input if it was an IP address literal.
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
     * Converts a string to an IP address literal to isCIDR format Range if this is possible.  If not
     *  possible, returns null
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
            boolean openBottom, openTop;
            byte lastByte = top[top.length - 1];

            if (lastByte == MAX_DIGIT) {
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
        } catch (Exception e){
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

        byte[] ip = ipToBytes(slashed[0]);
        if (ip.length == 4) {
            if (maskBits > 31) {
                barf("IPv4 mask bits must be < 32");
            }
        } else {
            if (maskBits > 127) {
                barf("IPv6 mask bits must be < 128");
            }
        }

        byte[] maxBytes;
        maxBytes = computeTopBytes(ip, maskBits);

        return new Range(toHexDigits(ip), false, toHexDigits(maxBytes), false, true);
    }

    private static byte[] computeTopBytes(final byte[] baseBytes, int maskBits) {

        if (baseBytes.length == 4) {
            maskBits = 32 - maskBits;
        } else {
            maskBits = 128 - maskBits;
        }
        byte[] maxBytes = new byte[baseBytes.length];

        for (int i = baseBytes.length - 1; i >= 0; i--) {
            if (maskBits >= 8) {
                maxBytes[i] = (byte) 0xff;
            } else if (maskBits <= 0) {
                maxBytes[i] = baseBytes[i];
            } else {
                maxBytes[i] = (byte) (baseBytes[i] | TRAILING_MAX_BITS[maskBits]);
            }
            maskBits -= 8;
        }
        return maxBytes;
    }

    private static void barf(final String msg) throws IllegalArgumentException {
        throw new IllegalArgumentException(msg);
    }

}
