package hyphanet.base;

/**
 * Utility class for formatting byte sizes into human-readable strings using IEC units (powers of
 * 1024). Provides methods to format sizes with and without spaces, and with optional non-breaking
 * spaces.
 *
 * <p>Uses suffixes like KiB, MiB, GiB, etc., as defined by IEC 60027-2.
 */
public final class SizeUtil {

  /**
   * Array of IEC suffixes used for size formatting.
   *
   * <p>The suffixes are in ascending order of magnitude, starting from bytes (B) up to yotta bytes
   * (YiB).
   */
  private static final String[] SUFFIXES = {
    "B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB"
  };

  /** Private constructor to prevent instantiation of this utility class. */
  private SizeUtil() {}

  /**
   * Formats a byte size into a human-readable string with a space between the number and the unit.
   *
   * <p>This method is a convenience overload of {@link #formatSize(long, boolean)} that defaults to
   * using a regular space.
   *
   * @param bytes The byte size to format.
   * @return A human-readable string representation of the size, e.g., "1.2 MiB".
   */
  public static String formatSize(long bytes) {
    return formatSize(bytes, false);
  }

  /**
   * Formats a byte size into a human-readable string without any space between the number and the
   * unit.
   *
   * <p>This method is useful when space is constrained or when concatenating the size with other
   * strings. It uses {@link #getFormattedSizeParts(long)} internally and joins the parts without a
   * separator.
   *
   * @param bytes The byte size to format.
   * @return A human-readable string representation of the size without space, e.g., "1.2MiB".
   */
  public static String formatSizeWithoutSpace(long bytes) {
    String[] result = getFormattedSizeParts(bytes);
    return result[0] + result[1];
  }

  /**
   * Formats a byte size into a human-readable string with a space between the number and the unit,
   * allowing for the option to use a non-breaking space.
   *
   * <p>A non-breaking space ({@code u00a0}) can be used to prevent line breaks between the number
   * and the unit, which can improve readability in some contexts.
   *
   * <p>Internally, this method uses {@link #getFormattedSizeParts(long)} to obtain the numerical
   * part and the unit suffix, and then combines them with the specified space character.
   *
   * @param bytes The byte size to format.
   * @param useNonBreakingSpace {@code true} to use a non-breaking space, {@code false} to use a
   *     regular space.
   * @return A human-readable string representation of the size, e.g., "1.2 MiB" (if {@code
   *     useNonBreakingSpace} is {@code true}) or "1.2 MiB" (if {@code false}).
   */
  public static String formatSize(long bytes, boolean useNonBreakingSpace) {
    String[] result = getFormattedSizeParts(bytes);
    return result[0] + (useNonBreakingSpace ? "\u00a0" : " ") + result[1];
  }

  /**
   * Gets the formatted size parts as a String array, containing the numerical value and the unit
   * suffix.
   *
   * <p>This method is the core of the size formatting logic. It determines the appropriate unit
   * suffix (KiB, MiB, etc.) based on the magnitude of the input byte size and formats the numerical
   * value to a reasonable precision.
   *
   * <p><b>Implementation Details:</b>
   *
   * <ul>
   *   <li><b>Handles Negative Sizes:</b> The method correctly handles negative byte sizes by
   *       preserving the sign in the output.
   *   <li><b>Iterates through Suffixes:</b> It iterates through the {@link #SUFFIXES} array to find
   *       the largest unit suffix that is smaller than or equal to the absolute byte size.
   *   <li><b>Unit Determination Logic:</b> The loop continues as long as the current size
   *       multiplier {@code s} is within {@link Long#MAX_VALUE} when multiplied by 1024 (to prevent
   *       overflow), and as long as {@code s * 1024} is still smaller than the absolute byte size.
   *       This ensures that the largest appropriate unit is selected.
   *   <li><b>Byte Case Handling:</b> If the size is in bytes (i.e., no suffix other than "B" is
   *       used), the method returns the raw byte value as an integer string without any decimal
   *       places.
   *   <li><b>Mantissa Calculation:</b> For larger sizes, the method calculates the mantissa by
   *       dividing the absolute byte size by the determined size multiplier {@code s}.
   *   <li><b>Precision Formatting:</b> The mantissa is converted to a string and formatted to a
   *       maximum of 3 or 4 characters to maintain readability. Specifically:
   *       <ul>
   *         <li>If the decimal point is at the 3rd position (e.g., "123.x"), it's truncated to 3
   *             characters (e.g., "123").
   *         <li>If a decimal point exists, it's not an exponential format ("E" is not present), and
   *             the string length is greater than 4, it's truncated to 4 characters (e.g., "1.234"
   *             from "1.2345").
   *       </ul>
   *   <li><b>Suffix Appending:</b> Finally, the formatted mantissa and the corresponding unit
   *       suffix from {@link #SUFFIXES} are returned as a String array. If the size is larger than
   *       the largest supported unit (YiB), an empty string is used as the suffix, and the mantissa
   *       represents a very large number.
   * </ul>
   *
   * @param bytes The byte size to format.
   * @return A String array containing two elements:
   *     <ul>
   *       <li>The first element is the formatted numerical value as a String.
   *       <li>The second element is the unit suffix (e.g., "KiB", "MiB", etc.) or an empty string
   *           if no suffix is applicable.
   *     </ul>
   */
  public static String[] getFormattedSizeParts(long bytes) {
    boolean negative = bytes < 0;
    long absBytes = negative ? -bytes : bytes;
    long s = 1;
    int i = 0;

    for (; i < SUFFIXES.length; i++) {
      if (s > Long.MAX_VALUE / 1024 // Largest supported size
          || s * 1024 > absBytes // Smaller than multiplier [i] - use the previous one
      ) {
        break;
      }

      s *= 1024;
    }

    if (s == 1) {
      // Bytes? Then we don't need real numbers with a comma
      return new String[] {(negative ? "-" : "") + absBytes, SUFFIXES[0]};
    }

    double mantissa = (double) absBytes / s;
    String o = Double.toString(mantissa);
    int dotIndex = o.indexOf('.');
    if (dotIndex == 3) {
      o = o.substring(0, 3);
    } else if (dotIndex > -1 && !o.contains("E") && o.length() > 4) {
      o = o.substring(0, 4);
    }
    if (negative) {
      o = "-" + o;
    }
    if (i < SUFFIXES.length) {
      return new String[] {o, SUFFIXES[i]};
    }
    return new String[] {o, ""};
  }
}
