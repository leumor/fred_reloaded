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

import static java.time.ZoneOffset.UTC;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * {@link TimeUtil} is a utility class for formatting and parsing time durations and dates. It
 * provides methods to convert time intervals (in milliseconds) into human-readable strings and to
 * parse time interval strings back into milliseconds. It also includes utilities for formatting
 * dates according to HTTP standards and for manipulating {@link Instant} objects.
 *
 * <p>This class is designed to be thread-safe and stateless. All methods are static and operate on
 * the provided input parameters without modifying any internal state.
 */
public final class TimeUtil {
  /**
   * Regular expression pattern used to split time interval strings into numeric values and unit
   * suffixes. The pattern `(?<=[a-z])` is a positive lookbehind assertion that matches the position
   * immediately preceded by a lowercase letter. This is used to split strings like "1w2d3h" into
   * ["1w", "2d", "3h"].
   *
   * @see #toMillis(String)
   */
  private static final Pattern TIME_INTERVAL_PATTERN = Pattern.compile("(?<=[a-z])");

  /** Private constructor to prevent instantiation of this utility class. */
  private TimeUtil() {}

  /**
   * Formats a given time interval in milliseconds into a human-readable string. The output string
   * will contain at most two terms representing the largest time units (weeks, days, hours,
   * minutes, seconds). For example, 3661000 milliseconds would be formatted as "1h1m".
   *
   * @param timeInterval The time interval to format, in milliseconds.
   * @return A formatted string representing the time interval, e.g., "1w2d", "3h4m", "5s". If the
   *     time interval is less than 1 second (and {@code withSecondFractions} is false), it returns
   *     "0s".
   * @see #formatTime(long, int, boolean)
   */
  public static String formatTime(long timeInterval) {
    return formatTime(timeInterval, 2, false);
  }

  /**
   * Formats a given time interval in milliseconds into a human-readable string, limiting the number
   * of terms. The output string will contain at most {@code maxTerms} terms representing the
   * largest time units. For example, with {@code maxTerms} set to 2, 3661000 milliseconds would be
   * formatted as "1h1m".
   *
   * @param timeInterval The time interval to format, in milliseconds.
   * @param maxTerms The maximum number of time units (terms) to include in the output string. Must
   *     be between 1 and 6 (inclusive).
   * @return A formatted string representing the time interval, e.g., "1w2d", "3h4m", "5s". If the
   *     time interval is less than 1 second (and {@code withSecondFractions} is false), it returns
   *     "0s".
   * @throws IllegalArgumentException if {@code maxTerms} is greater than 6.
   * @see #formatTime(long, int, boolean)
   */
  public static String formatTime(long timeInterval, int maxTerms) {
    return formatTime(timeInterval, maxTerms, false);
  }

  /**
   * Formats a given time interval in milliseconds into a human-readable string, with options for
   * term limits and second fractions. This method converts a time interval into a string
   * representation using weeks, days, hours, minutes, seconds, and optionally milliseconds. The
   * number of terms displayed is controlled by {@code maxTerms}, and the inclusion of second
   * fractions (milliseconds) is controlled by {@code withSecondFractions}.
   *
   * @param timeInterval The time interval to format, in milliseconds.
   * @param maxTerms The maximum number of time units (terms) to display. Must be between 1 and 6
   *     (inclusive).
   * @param withSecondFractions If {@code true}, includes seconds with fractional milliseconds in
   *     the output if applicable and if there are enough terms available according to {@code
   *     maxTerms}.
   * @return A formatted string representing the time interval, e.g., "1w2d3h", "4m5s", "6.789s". If
   *     the time interval is less than 1 second (and {@code withSecondFractions} is false), it
   *     returns "0s".
   * @throws IllegalArgumentException if {@code maxTerms} is greater than 6.
   */
  public static String formatTime(long timeInterval, int maxTerms, boolean withSecondFractions) {

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

    record TimeUnit(String suffix, long duration) {}

    TimeUnit[] units = {
      new TimeUnit("w", ChronoUnit.WEEKS.getDuration().toMillis()),
      new TimeUnit("d", ChronoUnit.DAYS.getDuration().toMillis()),
      new TimeUnit("h", ChronoUnit.HOURS.getDuration().toMillis()),
      new TimeUnit("m", ChronoUnit.MINUTES.getDuration().toMillis()),
      new TimeUnit("s", ChronoUnit.SECONDS.getDuration().toMillis())
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

  /**
   * Parses a time interval string (e.g., "1w2d3h4m5s") into milliseconds. The string can contain
   * weeks (w), days (d), hours (h), minutes (m), and seconds (s) units. It also supports fractional
   * seconds, e.g., "0.5s" or "1.234s". The input string can optionally start with a minus sign '-'
   * to represent a negative time interval.
   *
   * @param timeInterval The time interval string to parse, e.g., "1w2d3h", "4m5s", "-10s", "0.5s".
   * @return The parsed time interval in milliseconds.
   * @throws IllegalArgumentException if the input string contains an unknown time unit or is
   *     malformed.
   */
  public static long toMillis(String timeInterval) {
    boolean isNegative = timeInterval.startsWith("-");
    String sanitizedInterval = isNegative ? timeInterval.substring(1) : timeInterval;

    String[] terms = TIME_INTERVAL_PATTERN.split(sanitizedInterval, -1);
    long millis = 0;

    for (String term : terms) {
      if (term.isEmpty()) {
        continue;
      }

      char unit = term.charAt(term.length() - 1);
      String value = term.substring(0, term.length() - 1);

      millis +=
          switch (unit) {
            case 'w' -> ChronoUnit.WEEKS.getDuration().toMillis() * Long.parseLong(value);
            case 'd' -> ChronoUnit.DAYS.getDuration().toMillis() * Long.parseLong(value);
            case 'h' -> ChronoUnit.HOURS.getDuration().toMillis() * Long.parseLong(value);
            case 'm' -> ChronoUnit.MINUTES.getDuration().toMillis() * Long.parseLong(value);
            case 's' ->
                term.contains(".")
                    ? Integer.parseInt(term.replaceAll("[a-z.]", ""))
                    : ChronoUnit.SECONDS.getDuration().toMillis() * Long.parseLong(value);
            default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
          };
    }

    return isNegative ? -millis : millis;
  }

  /**
   * Formats a given time in milliseconds into an HTTP-compliant date string (RFC 1123 format). This
   * format is commonly used in HTTP headers like {@code Date} and {@code Expires}. It uses {@link
   * DateTimeFormatter#RFC_1123_DATE_TIME} for formatting.
   *
   * <p><b>Error Handling:</b> If a {@link DateTimeException} occurs during formatting (which can
   * happen for dates outside the valid range of {@link Instant}, such as dates beyond year 9999),
   * it catches the exception and returns a date string representing the latest possible date
   * (December 31, 9999, 23:59:59.999999999 UTC) in RFC 1123 format to prevent unexpected issues in
   * HTTP communication.
   *
   * @param time The time in milliseconds since the epoch to format.
   * @return An HTTP-compliant date string in RFC 1123 format, e.g., "Tue, 03 Jan 2023 10:00:00
   *     GMT".
   */
  public static String makeHttpDate(long time) {
    try {
      return DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(time).atZone(UTC));
    } catch (DateTimeException e) {
      // Handle dates beyond year 9999
      return DateTimeFormatter.RFC_1123_DATE_TIME.format(
          ZonedDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_999, UTC));
    }
  }

  /**
   * Sets the time component of a given {@link Instant} to zero (00:00:00.000). This method
   * truncates the instant to the beginning of the day, effectively setting the hours, minutes,
   * seconds, and nanoseconds to zero, while keeping the date (year, month, day) the same. It
   * utilizes {@link Instant#truncatedTo(TemporalUnit)} with {@link ChronoUnit#DAYS} to achieve
   * this.
   *
   * @param instant The {@link Instant} whose time component needs to be set to zero.
   * @return A new {@link Instant} representing the same date as the input instant but with the time
   *     set to 00:00:00.000.
   */
  public static Instant setTimeToZero(Instant instant) {
    return instant.truncatedTo(ChronoUnit.DAYS);
  }
}
