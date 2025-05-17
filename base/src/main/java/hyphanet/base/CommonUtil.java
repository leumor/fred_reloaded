package hyphanet.base;

import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.Nullable;

public final class CommonUtil {
  private CommonUtil() {}

  public static List<Byte> toByteList(@Nullable byte[] bytes) {
    if (bytes == null) {
      return List.of();
    }
    return List.of(ArrayUtils.toObject(bytes));
  }

  public static byte[] toByteArray(List<Byte> bytes) {
    return ArrayUtils.toPrimitive(bytes.toArray(new Byte[0]));
  }
}
