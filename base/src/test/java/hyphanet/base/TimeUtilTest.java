/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package hyphanet.base;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static java.util.Calendar.MILLISECOND;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for {@link hyphanet.base.TimeUtil} class.
 *
 * @author Alberto Bacchelli &lt;sback@freenetproject.org&gt;
 */
class TimeUtilTest {

    @BeforeAll
    static void setUp() {
        Locale.setDefault(Locale.US);
    }

    /**
     * Tests formatTime(long,int,boolean) method trying the biggest long value
     */
    @Test
    void testFormatTime_LongIntBoolean_MaxValue() {
        String expectedForMaxLongValue = "15250284452w3d7h12m55.807s";
        assertEquals(expectedForMaxLongValue, TimeUtil.formatTime(Long.MAX_VALUE, 6, true));
    }

    /**
     * Tests formatTime(long,int) method trying the biggest long value
     */
    @Test
    void testFormatTime_LongInt() {
        String expectedForMaxLongValue = "15250284452w3d7h12m55s";
        assertEquals(expectedForMaxLongValue, TimeUtil.formatTime(Long.MAX_VALUE, 6));
    }

    /**
     * Tests formatTime(long) method trying the biggest long value
     */
    @Test
    void testFormatTime_Long() {
        //it uses two terms by default
        String expectedForMaxLongValue = "15250284452w3d";
        assertEquals(expectedForMaxLongValue, TimeUtil.formatTime(Long.MAX_VALUE));
    }

    /**
     * Tests formatTime(long) method using known values. They could be checked using Google
     * Calculator <a
     * href="http://www.google.com/intl/en/help/features.html#calculator">...</a>
     */
    @Test
    void testFormatTime_KnownValues() {
        long methodLong;
        String[][] valAndExpected = {
            //one week
            {"604800000", "1w"},
            //one day
            {"86400000", "1d"},
            //one hour
            {"3600000", "1h"},
            //one minute
            {"60000", "1m"},
            //one second
            {"1000", "1s"}
        };
        for (String[] strings : valAndExpected) {
            methodLong = Long.parseLong(strings[0]);
            assertEquals(TimeUtil.formatTime(methodLong), strings[1]);
        }
    }

    /**
     * Tests formatTime(long,int) method using a long value that generate every possible term
     * kind. It tests if the maxTerms arguments works correctly
     */
    @Test
    void testFormatTime_LongIntBoolean_maxTerms() {
        String[] valAndExpected = {
            //0 terms
            "",
            //1 term
            "1w",
            //2 terms
            "1w1d",
            //3 terms
            "1w1d1h",
            //4 terms
            "1w1d1h1m",
            //5 terms
            "1w1d1h1m1s",
            //6 terms
            "1w1d1h1m1.001s"
        };
        for (int i = 0; i < valAndExpected.length; i++) {
            assertEquals(valAndExpected[i], TimeUtil.formatTime(oneForTermLong, i, true));
        }
    }

    /**
     * Tests formatTime(long,int) method using one millisecond time interval. It tests if the
     * withSecondFractions argument works correctly
     */
    @Test
    void testFormatTime_LongIntBoolean_milliseconds() {
        long methodValue = 1;    //1ms
        assertEquals("0s", TimeUtil.formatTime(methodValue, 6, false));
        assertEquals("0.001s", TimeUtil.formatTime(methodValue, 6, true));
    }

    /**
     * Tests formatTime(long,int) method using a long value that generate every possible term
     * kind. It tests if the maxTerms arguments works correctly
     */
    @Test
    void testFormatTime_LongIntBoolean_tooManyTerms() {
        try {
            TimeUtil.formatTime(oneForTermLong, 7);
            fail("Expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException anException) {
            assertNotNull(anException);
        }
    }

    /**
     * Tests {@link TimeUtil#setTimeToZero(java.time.Instant)}
     */
    @Test
    void testSetTimeToZero() {
        // Test whether zeroing doesn't happen when it needs not to.

        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.set(2015, Calendar.JANUARY /* 0-based! */, 1, 0, 0, 0);
        c.set(MILLISECOND, 0);

        Date original = c.getTime();
        Date zeroed = Date.from(TimeUtil.setTimeToZero(original.toInstant()));

        assertEquals(original, zeroed);
        // Date objects are mutable so their recycling is discouraged, check for it
        assertNotSame(original, zeroed);

        // Test whether zeroing happens when it should.

        c.set(2014, Calendar.DECEMBER /* 0-based! */, 31, 23, 59, 59);
        c.set(MILLISECOND, 999);
        original = c.getTime();
        Date originalBackup = (Date) original.clone();

        c.set(2014, Calendar.DECEMBER /* 0-based! */, 31, 0, 0, 0);
        c.set(MILLISECOND, 0);
        Date expected = c.getTime();

        zeroed = Date.from(TimeUtil.setTimeToZero(original.toInstant()));

        assertEquals(expected, zeroed);
        assertNotSame(original, zeroed);
        // Check for bogus tampering with original object
        assertEquals(originalBackup, original);
    }

    @Test
    void testToMillis_oneForTermLong() {
        assertEquals(TimeUtil.toMillis("1w1d1h1m1.001s"), oneForTermLong);
    }

    @Test
    void testToMillis_maxLong() {
        assertEquals(Long.MAX_VALUE, TimeUtil.toMillis("15250284452w3d7h12m55.807s"));
    }

    @Test
    void testToMillis_minLong() {
        assertEquals(Long.MIN_VALUE, TimeUtil.toMillis("-15250284452w3d7h12m55.808s"));
    }

    @Test
    void testToMillis_empty() {
        assertEquals(0, TimeUtil.toMillis(""));
        assertEquals(0, TimeUtil.toMillis("-"));
    }

    @Test
    void testToMillis_unknownFormat() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TimeUtil.toMillis("15250284452w3q7h12m55.807s")
        );
    }

    //1w+1d+1h+1m+1s+1ms
    private final long oneForTermLong = 694861001;
}
