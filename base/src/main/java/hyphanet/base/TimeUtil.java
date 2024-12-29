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

package hyphanet.base;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;

import static java.time.ZoneOffset.UTC;

/**
 * Time formatting utility. Formats milliseconds into a week/day/hour/second/milliseconds
 * string.
 */
public final class TimeUtil {
    private static final Pattern TIME_INTERVAL_PATTERN = Pattern.compile("(?<=[a-z])");

    private TimeUtil() {
    }

    public static String formatTime(long timeInterval) {
        return formatTime(timeInterval, 2, false);
    }

    public static String formatTime(long timeInterval, int maxTerms) {
        return formatTime(timeInterval, maxTerms, false);
    }

    /**
     * It converts a given time interval into a week/day/hour/second.milliseconds string.
     *
     * @param timeInterval        interval to convert, millis
     * @param maxTerms            the terms number to display (e.g. 2 means "h" and "m" if the
     *                            time could be expressed in hour, 3 means "h","m","s" in the
     *                            same example). The maximum terms number available is 6
     * @param withSecondFractions if true it displays seconds.milliseconds
     *
     * @return the formatted String
     */
    public static String formatTime(
        long timeInterval, int maxTerms, boolean withSecondFractions) {

        if (maxTerms > 6) {
            throw new IllegalArgumentException("Maximum terms cannot exceed 6");
        }

        if (!withSecondFractions && Math.abs(timeInterval) < 1000) {
            return "0s";
        }

        StringBuilder sb = new StringBuilder(64);

        long remaining = timeInterval;
        if (remaining < 0) {
            sb.append('-');
            remaining = Math.abs(remaining);
        }


        record TimeUnit(String suffix, long duration) {
        }

        TimeUnit[] units = {
            new TimeUnit("w", ChronoUnit.WEEKS.getDuration().toMillis()), new TimeUnit(
            "d",
                                                                                       ChronoUnit.DAYS.getDuration()
                                                                                                      .toMillis()
        ), new TimeUnit(
            "h",
            ChronoUnit.HOURS.getDuration().toMillis()
        ), new TimeUnit("m", ChronoUnit.MINUTES.getDuration().toMillis()), new TimeUnit(
            "s",
            ChronoUnit.SECONDS.getDuration().toMillis()
        )
        };

        int termCount = 0;
        for (TimeUnit unit : units) {
            if (termCount >= maxTerms || "s".equals(unit.suffix)) {
                break;
            }

            long value = remaining / unit.duration;
            if (value > 0) {
                sb.append(value).append(unit.suffix);
                remaining %= unit.duration;
                termCount++;
            }
        }

        if (withSecondFractions && (maxTerms - termCount) >= 2 && remaining > 0) {
            double fractionalSeconds = remaining / 1000.0D;
            sb.append(String.format(Locale.US, "%.3fs", fractionalSeconds));
        } else if (termCount < maxTerms) {
            var unit = units[units.length - 1];
            long seconds = remaining / unit.duration;
            if (seconds > 0) {
                sb.append(seconds).append(unit.suffix);
            }
        }

        return sb.toString();
    }

    public static long toMillis(String timeInterval) {
        boolean isNegative = timeInterval.startsWith("-");
        String sanitizedInterval = isNegative ? timeInterval.substring(1) : timeInterval;

        String[] terms = TIME_INTERVAL_PATTERN.split(sanitizedInterval);
        long millis = 0;

        for (String term : terms) {
            if (term.isEmpty()) {
                continue;
            }

            char unit = term.charAt(term.length() - 1);
            String value = term.substring(0, term.length() - 1);

            millis += switch (unit) {
                case 'w' -> ChronoUnit.WEEKS.getDuration().toMillis() * Long.parseLong(value);
                case 'd' -> ChronoUnit.DAYS.getDuration().toMillis() * Long.parseLong(value);
                case 'h' -> ChronoUnit.HOURS.getDuration().toMillis() * Long.parseLong(value);
                case 'm' ->
                    ChronoUnit.MINUTES.getDuration().toMillis() * Long.parseLong(value);
                case 's' -> term.contains(".") ? Integer.parseInt(term.replaceAll(
                    "[a-z.]",
                    ""
                )) : ChronoUnit.SECONDS.getDuration().toMillis() * Long.parseLong(value);
                default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
            };
        }

        return isNegative ? -millis : millis;
    }

    /**
     * Helper to format time HTTP conform
     */
    public static String makeHttpDate(long time) {
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(time)
                                                                      .atZone(UTC));
        } catch (DateTimeException e) {
            // Handle dates beyond year 9999
            return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.of(
                9999,
                12,
                31,
                23,
                59,
                59,
                999_999_999,
                UTC
            ));
        }
    }

    /**
     * @return Returns the passed date with the same year/month/day but with the time set to
     * 00:00:00.000
     */
    public static Instant setTimeToZero(Instant instant) {
        return instant.truncatedTo(ChronoUnit.DAYS);
    }
}
