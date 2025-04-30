package hyphanet.access.key;

public enum CompressionAlgorithm {
  NO_COMP(-1),
  GZIP(0),
  BZIP2(1),
  LZMA(2),
  LZMA_NEW(3);

  CompressionAlgorithm(int value) {
    this.value = value;
  }

  public static CompressionAlgorithm fromValue(int value) {
    for (CompressionAlgorithm algo : CompressionAlgorithm.values()) {
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
