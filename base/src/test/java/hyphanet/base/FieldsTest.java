/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package hyphanet.base;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for {@link Fields} class.
 *
 * @author stuart martin &lt;wavey@freenetproject.org&gt;
 */
public class FieldsTest {

    @Test
    public void testHexToLong() {

        long l1 = Fields.hexToLong("0");
        assertEquals(0, l1);

        l1 = Fields.hexToLong("000000");
        assertEquals(0, l1);

        l1 = Fields.hexToLong("1");
        assertEquals(1, l1);

        l1 = Fields.hexToLong("a");
        assertEquals(10, l1);

        l1 = Fields.hexToLong("ff");
        assertEquals(255, l1);

        l1 = Fields.hexToLong("ffffffff");
        assertEquals(4294967295L, l1);

        l1 = Fields.hexToLong("7fffffffffffffff");
        assertEquals(Long.MAX_VALUE, l1);

        l1 = Fields.hexToLong("8000000000000000");
        assertEquals(Long.MIN_VALUE, l1);

        l1 = Fields.hexToLong("FFfffFfF"); // mix case
        assertEquals(4294967295L, l1);

        assertThrows(NumberFormatException.class,
                     () -> Fields.hexToLong("abcdef123456789aa")); // 17 chars

        assertThrows(NumberFormatException.class,
                     () -> Fields.hexToLong("DeADC0dER")); // invalid char

        // see javadoc
        l1 = Fields.hexToLong(Long.toHexString(20));
        assertEquals(20, l1);

        l1 = Fields.hexToLong(Long.toHexString(Long.MIN_VALUE));
        assertEquals(Long.MIN_VALUE, l1);

        // see javadoc
        String longAsString = Long.toString(-1, 16);
        assertThrows(NumberFormatException.class, () -> Fields.hexToLong(longAsString));

    }

    @Test
    public void testHexToInt() {

        int i1 = Fields.hexToInt("0");
        assertEquals(0, i1);

        i1 = Fields.hexToInt("000000");
        assertEquals(0, i1);

        i1 = Fields.hexToInt("1");
        assertEquals(1, i1);

        i1 = Fields.hexToInt("a");
        assertEquals(10, i1);

        i1 = Fields.hexToInt("ff");
        assertEquals(255, i1);

        i1 = Fields.hexToInt("80000000");
        assertEquals(Integer.MIN_VALUE, i1);

        i1 = Fields.hexToInt("0000000080000000"); // 16 chars
        assertEquals(Integer.MIN_VALUE, i1);

        i1 = Fields.hexToInt("7fffffff");
        assertEquals(Integer.MAX_VALUE, i1);

        assertThrows(NumberFormatException.class, () -> Fields.hexToInt("0123456789abcdef0"));

        assertThrows(NumberFormatException.class,
                     () -> Fields.hexToInt("C0dER")); // invalid char

        // see javadoc
        i1 = Fields.hexToInt(Integer.toHexString(20));
        assertEquals(20, i1);

        i1 = Fields.hexToInt(Long.toHexString(Integer.MIN_VALUE));
        assertEquals(Integer.MIN_VALUE, i1);

        // see javadoc
        String integerAsString = Integer.toString(-1, 16);
        assertThrows(NumberFormatException.class, () -> Fields.hexToInt(integerAsString));
    }

    @Test
    public void testStringToBool() {
        assertTrue(Fields.stringToBool("true"));
        assertTrue(Fields.stringToBool("TRUE"));
        assertFalse(Fields.stringToBool("false"));
        assertFalse(Fields.stringToBool("FALSE"));

        assertThrows(NumberFormatException.class, () -> Fields.stringToBool("Free Tibet"));
        assertThrows(NumberFormatException.class, () -> Fields.stringToBool(null));
    }

    @Test
    public void testStringToBoolWithDefault() {
        assertTrue(Fields.stringToBool("true", false));
        assertFalse(Fields.stringToBool("false", true));
        assertTrue(Fields.stringToBool("TruE", false));
        assertFalse(Fields.stringToBool("faLSE", true));
        assertTrue(Fields.stringToBool("trueXXX", true));
        assertFalse(Fields.stringToBool("XXXFalse", false));
        assertTrue(Fields.stringToBool(null, true));
    }

    @Test
    public void testBoolToString() {
        assertEquals("true", Fields.boolToString(true));
        assertEquals("false", Fields.boolToString(false));
    }

    @Test
    public void testCommaListFromString() {
        String[] expected = new String[]{"one", "two", "three", "four"};
        String[] actual = Fields.commaList("one,two,     three    ,  four");

        for (int i = 0; i < expected.length; i++) {
            assertNotNull(actual);
            assertEquals(expected[i], actual[i]);
        }

        // null
        assertNull(Fields.commaList((String) null));

        // no items
        expected = new String[]{};
        actual = Fields.commaList("");
        assertNotNull(actual);
        assertEquals(expected.length, actual.length);
    }

    @Test
    public void testStringArrayToCommaList() {

        String[] input = new String[]{"one", "two", "three", "four"};

        String expected = "one,two,three,four";
        String actual = Fields.commaList(input);

        assertEquals(expected, actual);

        // empty
        input = new String[]{};

        expected = "";
        actual = Fields.commaList(input);

        assertEquals(expected, actual);
    }

    @Test
    public void testHashcodeForByteArray() {
        byte[] input = new byte[]{0, 1, 2, 3, 4, 5, 6, 7};

        assertEquals(67372036, Fields.hashCode(input));

        // empty
        input = new byte[]{};

        assertEquals(0, Fields.hashCode(input));
    }

    @Test
    public void testLongHashcode() {

        byte[] b1 = new byte[]{1, 1, 2, 2, 3, 3};
        byte[] b2 = new byte[]{2, 2, 3, 3, 4, 4};
        byte[] b3 = new byte[]{1, 1, 2, 2, 3, 3};

        Long l1 = Fields.longHashCode(b1);
        Long l2 = Fields.longHashCode(b2);
        Long l3 = Fields.longHashCode(b3);

        assertNotEquals(l1, l2);
        assertNotEquals(l2, l3);
        assertEquals(l3, l1); // should be same due to Fields.longHashcode
    }

    @Test
    public void testIntsToBytes() {
        int[] longs = new int[]{};
        doRoundTripIntsArrayToBytesArray(longs);

        longs = new int[]{Integer.MIN_VALUE};
        doRoundTripIntsArrayToBytesArray(longs);

        longs = new int[]{0, Integer.MAX_VALUE, Integer.MIN_VALUE};
        doRoundTripIntsArrayToBytesArray(longs);

        longs = new int[]{33685760, 51511577};
        doRoundTripIntsArrayToBytesArray(longs);
    }

    @Test
    public void testBytesToLongsException() {
        byte[] bytes = new byte[3];
        assertThrows(IllegalArgumentException.class,
                     () -> Fields.bytesToLongs(bytes, 0, bytes.length));
    }

    @Test
    public void testBytesToInt() {

        byte[] bytes = new byte[]{0, 1, 2, 2};

        int outLong = Fields.bytesToInt(bytes, 0);
        assertEquals(33685760, outLong);

        doTestRoundTripBytesArrayToInt(bytes);

        byte[] finalBytes = new byte[]{};
        assertThrows(IllegalArgumentException.class,
                     () -> doTestRoundTripBytesArrayToInt(finalBytes));

        bytes = new byte[]{1, 1, 1, 1};
        doTestRoundTripBytesArrayToInt(bytes);
    }

    @Test
    public void testLongsToBytes() {
        long[] longs = new long[]{};
        doRoundTripLongsArrayToBytesArray(longs);

        longs = new long[]{Long.MIN_VALUE};
        doRoundTripLongsArrayToBytesArray(longs);

        longs = new long[]{0L, Long.MAX_VALUE, Long.MIN_VALUE};
        doRoundTripLongsArrayToBytesArray(longs);

        longs = new long[]{3733393793879837L};
        doRoundTripLongsArrayToBytesArray(longs);
    }

    @Test
    public void testBytesToLongException() {
        byte[] bytes = new byte[3];
        assertThrows(IllegalArgumentException.class, () -> Fields.bytesToLong(bytes, 0));
    }

    @Test
    public void testBytesToLong() {

        byte[] bytes = new byte[]{0, 1, 2, 2, 1, 3, 6, 7};

        long outLong = Fields.bytesToLong(bytes);
        assertEquals(506095310989295872L, outLong);

        doTestRoundTripBytesArrayToLong(bytes);

        byte[] finalBytes = new byte[]{};
        assertThrows(IllegalArgumentException.class,
                     () -> doTestRoundTripBytesArrayToLong(finalBytes));

        bytes = new byte[]{1, 1, 1, 1, 1, 1, 1, 1};
        doTestRoundTripBytesArrayToLong(bytes);

    }

    @Test
    public void testTrimLines() {
        assertEquals("", Fields.trimLines(""));
        assertEquals("", Fields.trimLines("\n"));
        assertEquals("a\n", Fields.trimLines("a"));
        assertEquals("a\n", Fields.trimLines("a\n"));
        assertEquals("a\n", Fields.trimLines(" a\n"));
        assertEquals("a\n", Fields.trimLines(" a \n"));
        assertEquals("a\n", Fields.trimLines(" a\n"));
        assertEquals("a\n", Fields.trimLines("\na"));
        assertEquals("a\n", Fields.trimLines("\na\n"));
        assertEquals("a\nb\n", Fields.trimLines("a\nb"));
    }

    @Test
    public void testGetDigits() {
        assertEquals(1, Fields.getDigits("1.0", 0, true));
        assertEquals(0, Fields.getDigits("1.0", 0, false));
        assertEquals(1, Fields.getDigits("1.0", 1, false));
        assertEquals(0, Fields.getDigits("1.0", 1, true));
        assertEquals(1, Fields.getDigits("1.0", 2, true));
        assertEquals(0, Fields.getDigits("1.0", 2, false));
        Random r = new Random(88888);
        for (int i = 0; i < 1024; i++) {
            int digits = r.nextInt(20) + 1;
            int nonDigits = r.nextInt(20) + 1;
            int digits2 = r.nextInt(20) + 1;
            String s = generateDigits(r, digits) + generateNonDigits(r, nonDigits) +
                       generateDigits(r, digits2);
            assertEquals(0, Fields.getDigits(s, 0, false));
            assertEquals(digits, Fields.getDigits(s, 0, true));
            assertEquals(nonDigits, Fields.getDigits(s, digits, false));
            assertEquals(0, Fields.getDigits(s, digits, true));
            assertEquals(digits2, Fields.getDigits(s, digits + nonDigits, true));
            assertEquals(0, Fields.getDigits(s, digits + nonDigits, false));
        }
    }

    @Test
    public void testCompareVersion() {
        checkCompareVersionLessThan("1.0", "1.1");
        checkCompareVersionLessThan("1.0", "1.01");
        checkCompareVersionLessThan("1.0", "2.0");
        checkCompareVersionLessThan("1.0", "11.0");
        checkCompareVersionLessThan("1.0", "1.0.1");
        checkCompareVersionLessThan("1", "1.1");
        checkCompareVersionLessThan("1", "2");
        checkCompareVersionLessThan("test 1.0", "test 1.1");
        checkCompareVersionLessThan("best 1.0", "test 1.0");
        checkCompareVersionLessThan("test 1.0", "testing 1.0");
        checkCompareVersionLessThan("1.0", "test 1.0");
    }

    @Test
    public void testStringToLongOverflow() {
        assertThrows(NumberFormatException.class, () -> Fields.parseLong("9999999999GiB"),
                     "Value out of range for long: 1.0737418238926258E19");
    }

    private void doRoundTripIntsArrayToBytesArray(int[] ints) {
        byte[] bytes = Fields.intsToBytes(ints);
        assert (bytes.length == ints.length * 4);

        int[] outLongs = Fields.bytesToInts(bytes);
        for (int i = 0; i < ints.length; i++) {
            assertEquals(outLongs[i], ints[i]);
        }
        assertEquals(outLongs.length, ints.length);
    }

    private void doTestRoundTripBytesArrayToInt(byte[] inBytes) {

        int outLong = Fields.bytesToInt(inBytes, 0);
        byte[] outBytes = Fields.intToBytes(outLong);
        for (int i = 0; i < inBytes.length; i++) {
            assertEquals(outBytes[i], inBytes[i]);
        }
        assertEquals(outBytes.length, inBytes.length);
    }

    private void doRoundTripLongsArrayToBytesArray(long[] longs) {
        byte[] bytes = Fields.longsToBytes(longs);
        assert (bytes.length == longs.length * 8);

        long[] outLongs = Fields.bytesToLongs(bytes);
        for (int i = 0; i < longs.length; i++) {
            assertEquals(outLongs[i], longs[i]);
        }
        assertEquals(outLongs.length, longs.length);
    }

    private void doTestRoundTripBytesArrayToLong(byte[] inBytes) {

        long outLong = Fields.bytesToLong(inBytes);
        byte[] outBytes = Fields.longToBytes(outLong);
        for (int i = 0; i < inBytes.length; i++) {
            assertEquals(outBytes[i], inBytes[i]);
        }
        assertEquals(outBytes.length, inBytes.length);
    }

    private String generateDigits(Random r, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            char c = '0';
            c += (char) r.nextInt(10);
            sb.append(c);
        }
        return sb.toString();
    }

    private String generateNonDigits(Random r, int count) {
        final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
        final String NONDIGITS = "./\\_=+:" + ALPHABET + ALPHABET.toUpperCase();
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(NONDIGITS.charAt(r.nextInt(NONDIGITS.length())));
        }
        return sb.toString();
    }

    private void checkCompareVersionLessThan(String a, String b) {
        checkCompareVersionEquals(a, a);
        checkCompareVersionEquals(b, b);
        assert (Fields.compareVersion(a, b) < 0);
        assert (Fields.compareVersion(b, a) > 0);
    }

    private void checkCompareVersionEquals(String a, String b) {
        assertEquals(0, Fields.compareVersion(a, b));
    }
}
