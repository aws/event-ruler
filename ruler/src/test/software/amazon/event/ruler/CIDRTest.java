package software.amazon.event.ruler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CIDRTest {

    @Test
    public void SpotMalformedCIDRs() {
        String[] badCIDrs = {
                "192.168.0.1/33",
                "foo",
                "foo/bar/baz",
                "192.168.0.1/foo",
                "192.168.0.1/-3424",
                "snorkle/3",
                "10.10.10.10.10/3",
                "2.1.0.-1/3",
                "2400:6500:3:3:3:3:3:3:3:3FF00::36FB:1F80/122",
                "2400:6500:3:3:-5:3:3:3:3:3FF00::36FB:1F80/122",
                "2400:6500:3:3:3:3:FFFFFFFFFFF:3:3:3FF00::36FB:1F80/122",
                "2400:6500:FF00::36FB:1F80/222"
        };
        for (String bad : badCIDrs) {
            try {
                CIDR.cidr(bad);
                fail("Allowed bad CIDR: " + bad);
            } catch (Exception e) {
                //yay
            }
        }
    }

    @Test
    public void testToStringFailures() {
        String[] bads = {
                "700.168.0.1",
                "foo",
                "foo/bar/baz",
                "192.-3.0.1",
                "snorkle",
                "10.10.10.10.10",
                "2.1.0.-1",
                "2400:6500:3:3:-5:3:3:3:3:3FF00::36FB:1F80",
                "2400:6500:3:3:3:3:FFFFFFFFFFF:3:3:3FF00::36FB:1F80/122"
        };

        for (String bad : bads) {
            assertEquals(bad, CIDR.ipToStringIfPossible(bad));
        }
    }

    @Test
    public void TestDigitSequence() {

        byte[] l = Range.digitSequence((byte) '4', (byte) 'C', false, false);
        byte[] wanted = {'5', '6', '7', '8', '9', 'A', 'B'};
        for (int i = 0; i < wanted.length; i++) {
            assertEquals(wanted[i], l[i]);
        }
        l = Range.digitSequence((byte) '4', (byte) 'C', true, false);
        byte[] wanted2 = {'4', '5', '6', '7', '8', '9', 'A', 'B'};
        for (int i = 0; i < wanted2.length; i++) {
            assertEquals(wanted2[i], l[i]);
        }
        l = Range.digitSequence((byte) '4', (byte) 'C', false, true);
        byte[] wanted3 = {'5', '6', '7', '8', '9', 'A', 'B', 'C'};
        for (int i = 0; i < wanted3.length; i++) {
            assertEquals(wanted3[i], l[i]);
        }
        l = Range.digitSequence((byte) '4', (byte) 'C', true, true);
        byte[] wanted4 = {'4', '5', '6', '7', '8', '9', 'A', 'B', 'C'};
        for (int i = 0; i < wanted4.length; i++) {
            assertEquals(wanted4[i], l[i]);
        }

        byte F = (byte) 'F';
        try {
            byte[] got;
            got = Range.digitSequence((byte) 'F', (byte) 'F', false, true);
            assertEquals(1, got.length);
            assertEquals(F, got[0]);
            Range.digitSequence((byte) 'F', (byte) 'F', true, false);
            assertEquals(1, got.length);
            assertEquals(F, got[0]);
        } catch (RuntimeException e) {
            fail("Blew up on F-F seq");
        }

    }

    @Test
    public void TestToString() {
        String[] addresses = {
                "0011:2233:4455:6677:8899:aabb:ccdd:eeff",
                "2001:db8::ff00:42:8329",
                "::1",
                "10.0.0.0",
                "54.240.196.171",
                "192.0.2.0",
                "13.32.0.0",
                "27.0.0.0",
                "52.76.128.0",
                "34.192.0.0",
                "2400:6500:FF00::36FB:1F80",
                "2600:9000::",
                "2600:1F11::",
                "2600:1F14::"
        };

        String[] wanted= {
                "00112233445566778899AABBCCDDEEFF",
                "20010DB8000000000000FF0000428329",
                "00000000000000000000000000000001",
                "0A000000",
                "36F0C4AB",
                "C0000200",
                "0D200000",
                "1B000000",
                "344C8000",
                "22C00000",
                "24006500FF0000000000000036FB1F80",
                "26009000000000000000000000000000",
                "26001F11000000000000000000000000",
                "26001F14000000000000000000000000"
        };

        for (int i = 0; i < addresses.length; i++) {
            assertEquals(addresses[i], wanted[i], CIDR.ipToString(addresses[i]));
        }
    }

    @Test
    public void TestCorrectRanges() {

        String[] addresses = {
                // The first few do not specify the minimum IP address of the CIDR range. But these are still real
                // CIDRs. We must calculate the floor of the CIDR range ourselves.
                "0011:2233:4455:6677:8899:aabb:ccdd:eeff/24",
                "2001:db8::ff00:42:8329/24",
                "::1/24",
                "10.0.0.0/24",
                "54.240.196.171/24",
                "192.0.2.0/24",
                "13.32.0.0/15",
                "27.0.0.0/22",
                "52.76.128.0/17",
                "34.192.0.0/12",
                "2400:6500:FF00::36FB:1F80/122",
                "2600:9000::/28",
                "2600:1F11::/36",
                "2600:1F14::/35"
        };
        String[] wanted = {
                "00112200000000000000000000000000/001122FFFFFFFFFFFFFFFFFFFFFFFFFF:false/false (T:NUMERIC_RANGE)",
                "20010D00000000000000000000000000/20010DFFFFFFFFFFFFFFFFFFFFFFFFFF:false/false (T:NUMERIC_RANGE)",
                "00000000000000000000000000000000/000000FFFFFFFFFFFFFFFFFFFFFFFFFF:false/false (T:NUMERIC_RANGE)",
                "0A000000/0A0000FF:false/false (T:NUMERIC_RANGE)",
                "36F0C400/36F0C4FF:false/false (T:NUMERIC_RANGE)",
                "C0000200/C00002FF:false/false (T:NUMERIC_RANGE)",
                "0D200000/0D21FFFF:false/false (T:NUMERIC_RANGE)",
                "1B000000/1B0003FF:false/false (T:NUMERIC_RANGE)",
                "344C8000/344CFFFF:false/false (T:NUMERIC_RANGE)",
                "22C00000/22CFFFFF:false/false (T:NUMERIC_RANGE)",
                "24006500FF0000000000000036FB1F80/24006500FF0000000000000036FB1FBF:false/false (T:NUMERIC_RANGE)",
                "26009000000000000000000000000000/2600900FFFFFFFFFFFFFFFFFFFFFFFFF:false/false (T:NUMERIC_RANGE)",
                "26001F11000000000000000000000000/26001F110FFFFFFFFFFFFFFFFFFFFFFF:false/false (T:NUMERIC_RANGE)",
                "26001F14000000000000000000000000/26001F141FFFFFFFFFFFFFFFFFFFFFFF:false/false (T:NUMERIC_RANGE)"
        };

        Machine m = new Machine();
        for (int i = 0; i < addresses.length; i++) {
            String addr = addresses[i];
            Range c = CIDR.cidr(addr);
            tryAddingAsRule(m, c);
            assertEquals(wanted[i], c.toString());
        }
    }

    @Test
    public void TestCorrectRangesForSingleIpAddress() {

        String[] addresses = {
                "0011:2233:4455:6677:8899:aabb:ccdd:eeff",
                "2001:db8::ff00:42:8329",
                "::0",
                "::3",
                "::9",
                "::a",
                "::f",
                "2400:6500:FF00::36FB:1F80",
                "2400:6500:FF00::36FB:1F85",
                "2400:6500:FF00::36FB:1F89",
                "2400:6500:FF00::36FB:1F8C",
                "2400:6500:FF00::36FB:1F8F",
                "54.240.196.255",
                "255.255.255.255",
                "255.255.255.0",
                "255.255.255.5",
                "255.255.255.10",
                "255.255.255.16",
                "255.255.255.26",
                "255.255.255.27"
        };
        String[] wanted = {
                "00112233445566778899AABBCCDDEEFE/00112233445566778899AABBCCDDEEFF:true/false (T:NUMERIC_RANGE)",
                "20010DB8000000000000FF0000428329/20010DB8000000000000FF000042832A:false/true (T:NUMERIC_RANGE)",
                "00000000000000000000000000000000/00000000000000000000000000000001:false/true (T:NUMERIC_RANGE)",
                "00000000000000000000000000000003/00000000000000000000000000000004:false/true (T:NUMERIC_RANGE)",
                "00000000000000000000000000000009/0000000000000000000000000000000A:false/true (T:NUMERIC_RANGE)",
                "0000000000000000000000000000000A/0000000000000000000000000000000B:false/true (T:NUMERIC_RANGE)",
                "0000000000000000000000000000000E/0000000000000000000000000000000F:true/false (T:NUMERIC_RANGE)",
                "24006500FF0000000000000036FB1F80/24006500FF0000000000000036FB1F81:false/true (T:NUMERIC_RANGE)",
                "24006500FF0000000000000036FB1F85/24006500FF0000000000000036FB1F86:false/true (T:NUMERIC_RANGE)",
                "24006500FF0000000000000036FB1F89/24006500FF0000000000000036FB1F8A:false/true (T:NUMERIC_RANGE)",
                "24006500FF0000000000000036FB1F8C/24006500FF0000000000000036FB1F8D:false/true (T:NUMERIC_RANGE)",
                "24006500FF0000000000000036FB1F8E/24006500FF0000000000000036FB1F8F:true/false (T:NUMERIC_RANGE)",
                "36F0C4FE/36F0C4FF:true/false (T:NUMERIC_RANGE)",
                "FFFFFFFE/FFFFFFFF:true/false (T:NUMERIC_RANGE)",
                "FFFFFF00/FFFFFF01:false/true (T:NUMERIC_RANGE)",
                "FFFFFF05/FFFFFF06:false/true (T:NUMERIC_RANGE)",
                "FFFFFF0A/FFFFFF0B:false/true (T:NUMERIC_RANGE)",
                "FFFFFF10/FFFFFF11:false/true (T:NUMERIC_RANGE)",
                "FFFFFF1A/FFFFFF1B:false/true (T:NUMERIC_RANGE)",
                "FFFFFF1B/FFFFFF1C:false/true (T:NUMERIC_RANGE)"
        };

        Machine m = new Machine();
        for (int i = 0; i < addresses.length; i++) {
            String addr = addresses[i];
            Range c = CIDR.ipToRangeIfPossible(addr);
            tryAddingAsRule(m, c);
            assertEquals(wanted[i], c.toString());
        }
        assertTrue(!m.isEmpty());
        for (int i = addresses.length - 1; i >= 0; i--) {
            String addr = addresses[i];
            Range c = CIDR.ipToRangeIfPossible(addr);
            assertEquals(wanted[i], c.toString());
            tryDeletingAsRule(m, c);
        }
        assertTrue(m.isEmpty());
    }

    @Test
    public void testInvalidIPMatchedByIPv6Regex() throws Exception {
        String invalidIpRule = "{ \"a\": [ \"08:23\" ] }";
        Machine machine = new Machine();
        machine.addRule("r1", invalidIpRule);
        assertEquals(Arrays.asList("r1"), machine.rulesForJSONEvent("{ \"a\": [ \"08:23\" ] }"));
    }

    private void tryAddingAsRule(Machine m, Range r) {
        try {
            List<Patterns> lp = new ArrayList<>();
            lp.add(r);
            Map<String, List<Patterns>> map = new HashMap<>();
            map.put("a", lp);
            m.addPatternRule("x", map);
        } catch (Exception e) {
            fail("Failed to add: " + r);
        }
    }

    private void tryDeletingAsRule(Machine m, Range r) {
        try {
            List<Patterns> lp = new ArrayList<>();
            lp.add(r);
            Map<String, List<Patterns>> map = new HashMap<>();
            map.put("a", lp);
            m.deletePatternRule("x", map);
        } catch (Exception e) {
            fail("Failed to add: " + r);
        }
    }
}