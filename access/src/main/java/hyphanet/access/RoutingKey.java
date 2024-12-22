package hyphanet.access;

import hyphanet.support.Base64;
import hyphanet.support.IllegalBase64Exception;

public record RoutingKey(byte[] bytes) {
    public RoutingKey {
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Routing key must not be empty");
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

    public int length() {
        return bytes.length;
    }
}
