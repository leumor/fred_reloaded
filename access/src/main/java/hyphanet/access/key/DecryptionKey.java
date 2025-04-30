package hyphanet.access.key;

import hyphanet.base.Base64;
import hyphanet.base.IllegalBase64Exception;

public record DecryptionKey(byte[] bytes) {
  public static final int DECRYPTION_KEY_LENGTH = 32;

  public DecryptionKey {
    if (bytes.length != DECRYPTION_KEY_LENGTH) {
      throw new IllegalArgumentException("Crypto key must be 32 bytes");
    }
  }

  public static DecryptionKey fromBase64(String base64Str) {
    try {
      var bytes = Base64.decode(base64Str);
      return new DecryptionKey(bytes);
    } catch (IllegalBase64Exception e) {
      throw new IllegalArgumentException("Invalid base64 crypto key", e);
    }
  }

  @Override
  public String toString() {
    return Base64.encode(bytes);
  }
}
