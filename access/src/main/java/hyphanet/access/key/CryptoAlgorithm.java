package hyphanet.access.key;

public enum CryptoAlgorithm {
  ALGO_AES_PCFB_256_SHA256(2),
  ALGO_AES_CTR_256_SHA256(3);

  CryptoAlgorithm(int value) {
    this.value = value;
  }

  public static CryptoAlgorithm fromValue(int value) {
    for (CryptoAlgorithm algo : CryptoAlgorithm.values()) {
      if (algo.value == value) {
        return algo;
      }
    }
    throw new IllegalArgumentException("Unknown value: " + value);
  }

  public int getValue() {
    return value;
  }

  private final int value;
}
