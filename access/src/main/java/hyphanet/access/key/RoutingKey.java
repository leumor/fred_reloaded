package hyphanet.access.key;

import hyphanet.base.Base64;
import hyphanet.base.IllegalBase64Exception;

public record RoutingKey(byte[] bytes) {
  public static final int ROUTING_KEY_LENGTH = 32;

  public RoutingKey {
    if (bytes.length != ROUTING_KEY_LENGTH) {
      throw new IllegalArgumentException("Routing key must be 32 bytes");
    }
  }

  public static RoutingKey fromBase64(String base64Str) {
    try {
      var bytes = Base64.decode(base64Str);
      return new RoutingKey(bytes);
    } catch (IllegalBase64Exception e) {
      throw new IllegalArgumentException("Invalid base64 routing key", e);
    }
  }

  @Override
  public String toString() {
    return Base64.encode(bytes);
  }
}
