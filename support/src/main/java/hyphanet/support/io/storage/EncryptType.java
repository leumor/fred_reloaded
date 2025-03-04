package hyphanet.support.io.storage;

import hyphanet.crypt.CryptByteBuffer;
import hyphanet.crypt.key.KeyType;
import hyphanet.crypt.mac.MacType;
import java.util.HashMap;
import java.util.Map;
import org.bouncycastle.crypto.SkippingStreamCipher;
import org.bouncycastle.crypto.engines.ChaChaEngine;
import org.jspecify.annotations.Nullable;

/**
 * Defines the encryption algorithms, MAC types, and associated parameters for different encryption
 * configurations.
 */
public enum EncryptType {
  /** ChaCha encryption with 128-bit key strength */
  CHACHA_128(1, 12, CryptByteBuffer.Type.CHACHA_128, MacType.HMAC_SHA_256, 32),

  /** ChaCha encryption with 256-bit key strength */
  CHACHA_256(2, 12, CryptByteBuffer.Type.CHACHA_256, MacType.HMAC_SHA_256, 32);

  public final int bitmask;
  public final int headerLen; // bytes
  public final CryptByteBuffer.Type encryptType;
  public final KeyType encryptKey;
  public final MacType macType;
  public final KeyType macKey;
  public final int macLen; // bytes
  private static final Map<Integer, EncryptType> byBitmask = new HashMap<>();

  static {
    for (EncryptType type : values()) {
      byBitmask.put(type.bitmask, type);
    }
  }

  /**
   * Creates the ChaCha enum values.
   *
   * @param bitmask The version number
   * @param magAndVerLen Length of magic value and version
   * @param type Alg to use for encrypting the data
   * @param macType Alg to use for MAC generation
   * @param macLen The length of the MAC output in bytes
   */
  EncryptType(
      int bitmask, int magAndVerLen, CryptByteBuffer.Type type, MacType macType, int macLen) {
    this.bitmask = bitmask;
    this.encryptType = type;
    this.encryptKey = type.keyType;
    this.macType = macType;
    this.macKey = macType.keyType;
    this.macLen = macLen;
    this.headerLen = magAndVerLen + (encryptKey.keySize >> 3) + (encryptKey.ivSize >> 3) + macLen;
  }

  public static @Nullable EncryptType getByBitmask(int val) {
    return byBitmask.get(val);
  }

  /** Returns an instance of the SkippingStreamCipher the goes with the current enum value. */
  public final SkippingStreamCipher get() {
    return new ChaChaEngine();
  }
}
