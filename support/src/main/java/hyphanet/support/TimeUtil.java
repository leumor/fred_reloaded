/*
  TimeUtil.java / Freenet
  Copyright (C) 2005-2006 The Free Network project
  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License as
  published by the Free Software Foundation; either version 2 of
  the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package hyphanet.support;

import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;

import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.TimeUnit.*;


/**
 * Utility class for time-related operations, providing methods to format time intervals,
 * parse duration strings, and handle HTTP date formatting.
 *
 * <p>This class handles various time-related operations including:
 * <ul>
 *   <li>Converting milliseconds to human-readable duration strings</li>
 *   <li>Parsing duration strings back to milliseconds</li>
 *   <li>Formatting dates for HTTP headers</li>
 *   <li>Basic date manipulation operations</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * String duration = TimeUtil.formatTime(3600000); // "1h"
 * long millis = TimeUtil.toMillis("1h30m"); // 5400000
 * }</pre>
 *
 * @since 1.0
 */
public class TimeUtil {


    /**
     * /**
     * Converts a time interval to a human-readable string representation.
     *
     * @param timeInterval        the time interval in milliseconds
     * @param maxTerms            maximum number of units to display (1-6) (e.g. 2 means "h" and "m" if the time could be expressed in hour,
     *                            3 means "h","m","s" in the same example)
     * @param withSecondFractions whether to include seconds with millisecond precision
     * @return formatted duration string (e.g., "1w2d3h" for 1 week, 2 days, 3 hours)
     * @throws IllegalArgumentException if maxTerms is greater than 6
     */
    public static String formatTime(long timeInterval, int maxTerms, boolean withSecondFractions) {

        // Early return for 0
        if (maxTerms == 0) {
            return "";
        }

        if (maxTerms > 6 || maxTerms < 0) {
            throw new IllegalArgumentException("maxTerms must be between 0 and 6");
        }

        // Early return for 0
        if (timeInterval == 0) {
            return "0s";
        }

        StringBuilder result = new StringBuilder(64);
        long remaining = timeInterval;

        if (remaining < 0) {
            result.append('-');
            remaining = Math.abs(remaining);
        }

        // Handle small values without fractions
        if (!withSecondFractions && remaining < 1000) {
            return "0s";
        }

        Duration duration = Duration.ofMillis(remaining);
        int terms = 0;

        // Weeks (Duration doesn't have weeks, so we calculate from days)
        long totalDays = duration.toDays();
        long weeks = totalDays / 7;
        if (weeks > 0) {
            result.append(weeks).append('w');
            terms++;
            duration = duration.minusDays(weeks * 7);
            if (terms >= maxTerms) return result.toString();
        }

        // Days (remaining days after weeks)
        long days = duration.toDays();
        if (days > 0) {
            result.append(days).append('d');
            terms++;
            duration = duration.minusDays(days);
            if (terms >= maxTerms) return result.toString();
        }

        // Hours
        long hours = duration.toHoursPart();
        if (hours > 0) {
            result.append(hours).append('h');
            terms++;
            duration = duration.minusHours(hours);
            if (terms >= maxTerms) return result.toString();
        }

        // Minutes
        long minutes = duration.toMinutesPart();
        if (minutes > 0) {
            result.append(minutes).append('m');
            terms++;
            duration = duration.minusMinutes(minutes);
            if (terms >= maxTerms) return result.toString();
        }

        // Seconds and fractions
        if (withSecondFractions && ((maxTerms - terms) >= 2)) {
            double seconds = duration.toMillis() / 1000.0D;
            if (seconds > 0) {
                DecimalFormat fix3 = new DecimalFormat("0.000");
                result.append(fix3.format(seconds)).append('s');
            }
        } else {
            long seconds = duration.toSecondsPart();
            if (seconds > 0) {
                result.append(seconds).append('s');
            }
        }

        return result.toString();
    }

    public static String formatTime(long timeInterval) {
        return formatTime(timeInterval, 2, false);
    }

    public static String formatTime(long timeInterval, int maxTerms) {
        return formatTime(timeInterval, maxTerms, false);
    }

    /**
     * Parses a duration string into milliseconds.
     * Accepts formats like "1w2d3h4m5s" or "1.23s".
     *
     * @param timeInterval duration string (e.g., "1w2d3h" or "500.123s")
     * @return duration in milliseconds
     * @throws NumberFormatException    if the format is invalid
     * @throws IllegalArgumentException if timeInterval is null or empty
     */
    public static long toMillis(String timeInterval) {
        if (timeInterval == null || timeInterval.isEmpty()) {
            return 0;
        }

        byte sign = 1;
        if (timeInterval.startsWith("-")) {
            sign = -1;
            timeInterval = timeInterval.substring(1);
        }

        // Split on letter boundaries, keeping the letter
        String[] terms = timeInterval.split("(?<=[a-z])");
        long millis = 0;

        for (String term : terms) {
            if (term.isEmpty()) continue;

            char measure = term.charAt(term.length() - 1);
            String value = term.substring(0, term.length() - 1);

            millis += switch (measure) {
                case 'w' -> MILLISECONDS.convert(Long.parseLong(value), DAYS) * 7;
                case 'd' -> MILLISECONDS.convert(Short.parseShort(value), DAYS);
                case 'h' -> MILLISECONDS.convert(Short.parseShort(value), HOURS);
                case 'm' -> MILLISECONDS.convert(Short.parseShort(value), MINUTES);
                case 's' -> {
                    if (term.contains(".")) {
                        yield Long.parseLong(term.replaceAll("[a-z.]", ""));
                    }
                    yield MILLISECONDS.convert(Short.parseShort(value), SECONDS);
                }
                default -> throw new NumberFormatException("Unknown format: " +
                        (sign > 0 ? "" : "-") + timeInterval);
            };
        }

        return millis * sign;
    }

    /**
     * Formats a timestamp as an HTTP-compliant date string.
     *
     * @param time timestamp in milliseconds
     * @return formatted date string in HTTP format
     */
    public static String makeHTTPDate(long time) {
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(Instant.ofEpochMilli(time)
                            .atZone(UTC));
        } catch (DateTimeException e) {
            // Handle dates beyond year 9999
            return DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(ZonedDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_999, UTC));
        }
    }

    /**
     * Sets the time portion of a date to midnight (00:00:00.000).
     *
     * @param date the date to modify
     * @return new date with time set to midnight UTC
     * @throws NullPointerException if date is null
     */
    public static Date setTimeToZero(final Date date) {
        Objects.requireNonNull(date, "Date cannot be null");
        return Date.from(
                LocalDateTime.ofInstant(date.toInstant(), UTC)
                        .toLocalDate()
                        .atStartOfDay(UTC)
                        .toInstant()
        );
    }
}
