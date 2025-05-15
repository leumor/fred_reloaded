package hyphanet.access.key;

import hyphanet.base.Base64;
import hyphanet.base.CommonUtil;
import hyphanet.base.IllegalBase64Exception;
import java.util.List;

public record DecryptionKey(List<Byte> data) {
  public static final int DECRYPTION_KEY_LENGTH = 32;

  public DecryptionKey {
    if (data.size() != DECRYPTION_KEY_LENGTH) {
      throw new IllegalArgumentException("Crypto key must be 32 bytes");
    }
  }

  public DecryptionKey(byte[] byteArr) {
    this(CommonUtil.toByteList(byteArr));
  }

  public static DecryptionKey fromBase64(String base64Str) {
    try {
      var byteArr = Base64.decode(base64Str);
      return new DecryptionKey(byteArr);
    } catch (IllegalBase64Exception e) {
      throw new IllegalArgumentException("Invalid base64 crypto key", e);
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
