package hyphanet.access.key;

import hyphanet.base.Base64;
import hyphanet.base.CommonUtil;
import hyphanet.base.IllegalBase64Exception;
import java.util.List;
import java.util.Objects;

public record RoutingKey(List<Byte> data) {
  public static final int ROUTING_KEY_LENGTH = 32;

  public RoutingKey {
    if (data.size() != ROUTING_KEY_LENGTH) {
      throw new IllegalArgumentException("Routing key must be 32 bytes");
    }
    data = List.copyOf(Objects.requireNonNull(data));
  }

  public RoutingKey(byte[] byteArr) {
    this(CommonUtil.toByteList(byteArr));
  }

  public static RoutingKey fromBase64(String base64Str) {
    try {
      var byteArr = Base64.decode(base64Str);
      return new RoutingKey(byteArr);
    } catch (IllegalBase64Exception e) {
      throw new IllegalArgumentException("Invalid base64 routing key", e);
    }
  }

  public byte[] getBytes() {
    return CommonUtil.toByteArray(data);
  }

  @Override
  public String toString() {
    return Base64.encode(CommonUtil.toByteArray(data));
  }
}
