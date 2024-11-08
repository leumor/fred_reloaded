package hyphanet.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.stream.Stream;

import static java.util.Calendar.MILLISECOND;
import static org.junit.jupiter.api.Assertions.*;

public class TimeUtilTest {
    /**
     * Tests formatTime(long,int,boolean) method
     * trying the biggest long value
     */
    @Test
    public void testFormatTime_LongIntBoolean_MaxValue() {
        String expectedForMaxLongValue = "15250284452w3d7h12m55.807s";
        assertEquals(TimeUtil.formatTime(Long.MAX_VALUE, 6, true),
                expectedForMaxLongValue);
    }

    /**
     * Tests formatTime(long,int) method
     * trying the biggest long value
     */
    @Test
    public void testFormatTime_LongInt() {
        String expectedForMaxLongValue = "15250284452w3d7h12m55s";
        assertEquals(TimeUtil.formatTime(Long.MAX_VALUE, 6),
                expectedForMaxLongValue);
    }

    /**
     * Tests formatTime(long) method
     * trying the biggest long value
     */
    @Test
    public void testFormatTime_Long() {
        //it uses two terms by default
        String expectedForMaxLongValue = "15250284452w3d";
        assertEquals(TimeUtil.formatTime(Long.MAX_VALUE),
                expectedForMaxLongValue);
    }

    /**
     * Tests formatTime(long) method
     * using known values.
     * They could be checked using Google Calculator
     * http://www.google.com/intl/en/help/features.html#calculator
     */
    @Test
    public void testFormatTime_KnownValues() {
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
            var methodLong = Long.parseLong(strings[0]);
            assertEquals(TimeUtil.formatTime(methodLong),
                    strings[1]);
        }
    }

    /**
     * Tests formatTime(long,int) method
     * using a long value that generate every possible
     * term kind. It tests if the maxTerms arguments
     * works correctly
     */
    @Test
    public void testFormatTime_LongIntBoolean_maxTerms() {
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
        for (int i = 0; i < valAndExpected.length; i++)
            assertEquals(TimeUtil.formatTime(oneForTermLong, i, true),
                    valAndExpected[i]);
    }

    /**
     * Tests formatTime(long,int) method
     * using one millisecond time interval.
     * It tests if the withSecondFractions argument
     * works correctly
     */
    @Test
    public void testFormatTime_LongIntBoolean_milliseconds() {
        long methodValue = 1;    //1ms
        assertEquals(TimeUtil.formatTime(methodValue, 6, false), "0s");
        assertEquals(TimeUtil.formatTime(methodValue, 6, true), "0.001s");
    }

    /**
     * Tests formatTime(long,int) method
     * using a long value that generate every possible
     * term kind. It tests if the maxTerms arguments
     * works correctly
     */
    @Test
    public void testFormatTime_LongIntBoolean_tooManyTerms() {
        try {
            TimeUtil.formatTime(oneForTermLong, 7);
            fail("Expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException anException) {
            assertNotNull(anException);
        }
    }


    @Test
    public void testToMillis_oneForTermLong() {
        assertEquals(TimeUtil.toMillis("1w1d1h1m1.001s"), oneForTermLong);
    }

    @Test
    public void testToMillis_maxLong() {
        assertEquals(TimeUtil.toMillis("15250284452w3d7h12m55.807s"), Long.MAX_VALUE);
    }

    @Test
    public void testToMillis_minLong() {
        assertEquals(TimeUtil.toMillis("-15250284452w3d7h12m55.808s"), Long.MIN_VALUE);
    }

    @Test
    public void testToMillis_empty() {
        assertEquals(TimeUtil.toMillis(""), 0);
        assertEquals(TimeUtil.toMillis("-"), 0);
    }

    @Test
    public void testToMillis_unknownFormat() {
        try {
            TimeUtil.toMillis("15250284452w3q7h12m55.807s");
        } catch (NumberFormatException e) {
            assertNotNull(e);
        }
    }

    /**
     * Tests {@link TimeUtil#setTimeToZero(Date)}
     */
    @Test
    public void testSetTimeToZero() {
        // Test whether zeroing doesn't happen when it needs not to.

        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.set(2015, 0 /* 0-based! */, 01, 00, 00, 00);
        c.set(MILLISECOND, 0);

        Date original = c.getTime();
        Date zeroed = TimeUtil.setTimeToZero(original);

        assertEquals(original, zeroed);
        // Date objects are mutable so their recycling is discouraged, check for it
        assertNotSame(original, zeroed);

        // Test whether zeroing happens when it should.

        c.set(2014, 12 - 1 /* 0-based! */, 31, 23, 59, 59);
        c.set(MILLISECOND, 999);
        original = c.getTime();
        Date originalBackup = (Date) original.clone();

        c.set(2014, 12 - 1 /* 0-based! */, 31, 00, 00, 00);
        c.set(MILLISECOND, 0);
        Date expected = c.getTime();

        zeroed = TimeUtil.setTimeToZero(original);

        assertEquals(expected, zeroed);
        assertNotSame(original, zeroed);
        // Check for bogus tampering with original object
        assertEquals(originalBackup, original);
    }

    @Test
    @DisplayName("Should format current time correctly")
    void makeHTTPDate_currentTime() {
        // Given
        long currentTimeMillis = System.currentTimeMillis();

        // When
        String result = TimeUtil.makeHTTPDate(currentTimeMillis);

        // Then
        assertNotNull(result);
        assertTrue(result.matches("^[A-Za-z]{3}, \\d{1,2} [A-Za-z]{3} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT$"));
    }

    @ParameterizedTest(name = "Timestamp {0} should format to {1}")
    @MethodSource("provideTimestampsAndExpectedFormats")
    void makeHTTPDate_specificDates(long timestamp, String expected) {
        // When
        String result = TimeUtil.makeHTTPDate(timestamp);

        // Then
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Should handle negative timestamps")
    void makeHTTPDate_negativeTimestamp() {
        // Given
        long timestamp = -86400000L; // One day before Unix epoch

        // When
        String result = TimeUtil.makeHTTPDate(timestamp);

        // Then
        assertEquals("Wed, 31 Dec 1969 00:00:00 GMT", result);
    }

    @Test
    @DisplayName("Should handle maximum timestamp")
    void makeHTTPDate_maxTimestamp() {
        // Given
        long maxTimestamp = Long.MAX_VALUE;

        // When
        String result = TimeUtil.makeHTTPDate(maxTimestamp);

        // Then
        assertNotNull(result);
        assertTrue(result.matches("^[A-Za-z]{3}, \\d{2} [A-Za-z]{3} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT$"));
    }

    @Test
    @DisplayName("Should maintain UTC/GMT timezone")
    void makeHTTPDate_timezoneConsistency() {
        // Given
        ZonedDateTime dateTime = ZonedDateTime.of(2023, 11, 7, 12, 0, 0, 0, ZoneId.of("UTC"));
        long timestamp = dateTime.toInstant().toEpochMilli();

        // When
        String result = TimeUtil.makeHTTPDate(timestamp);

        // Then
        assertTrue(result.endsWith("GMT"));
        assertEquals("Tue, 7 Nov 2023 12:00:00 GMT", result);
    }

    @Test
    @DisplayName("Should format leap year date correctly")
    void makeHTTPDate_leapYear() {
        // Given
        ZonedDateTime leapDay = ZonedDateTime.of(2024, 2, 29, 12, 0, 0, 0, ZoneId.of("UTC"));
        long timestamp = leapDay.toInstant().toEpochMilli();

        // When
        String result = TimeUtil.makeHTTPDate(timestamp);

        // Then
        assertEquals("Thu, 29 Feb 2024 12:00:00 GMT", result);
    }

    private static Stream<Arguments> provideTimestampsAndExpectedFormats() {
        return Stream.of(
                // Unix epoch
                Arguments.of(
                        0L,
                        "Thu, 1 Jan 1970 00:00:00 GMT"
                ),
                // Y2K
                Arguments.of(
                        946684800000L,
                        "Sat, 1 Jan 2000 00:00:00 GMT"
                ),
                // Specific date
                Arguments.of(
                        1699359600000L,
                        "Tue, 7 Nov 2023 12:20:00 GMT"
                )
        );
    }
    private final long oneForTermLong = 694861001;

}
