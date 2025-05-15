package hyphanet.base;

import java.util.List;
import org.apache.commons.lang3.ArrayUtils;

public final class CommonUtil {
  private CommonUtil() {}

  public static List<Byte> toByteList(byte[] bytes) {
    return List.of(ArrayUtils.toObject(bytes));
  }

  public static byte[] toByteArray(List<Byte> bytes) {
    return ArrayUtils.toPrimitive(bytes.toArray(new Byte[0]));
  }
}
