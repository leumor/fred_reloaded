package hyphanet.base;

import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.Nullable;

/**
 * Provides common utility methods.
 */
public final class CommonUtil {
  private CommonUtil() {}

  /**
   * Converts a byte array to an unmodifiable {@link List} of {@link Byte} objects.
   * <p>
   * If the input array is {@code null}, an empty list is returned.
   *
   * @param bytes the byte array to convert, may be {@code null}
   * @return an unmodifiable {@link List} containing the {@link Byte} objects from the input array,
   *         or an empty list if the input was {@code null}
   */
  public static List<Byte> toByteList(@Nullable byte[] bytes) {
    if (bytes == null) {
      return List.of();
    }
    return List.of(ArrayUtils.toObject(bytes));
  }

  /**
   * Converts a {@link List} of {@link Byte} objects to a primitive byte array.
   *
   * @param bytes the {@link List} of {@link Byte} objects to convert
   * @return a byte array containing the primitive byte values from the input list.
   *         Returns an empty array if the input list is empty.
   * @throws NullPointerException if the input list is {@code null}
   */
  public static byte[] toByteArray(List<Byte> bytes) {
    return ArrayUtils.toPrimitive(bytes.toArray(new Byte[0]));
  }
}
