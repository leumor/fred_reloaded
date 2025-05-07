package hyphanet.access.key;

public enum CompressionAlgorithm {
  NO_COMP((short) -1),
  GZIP((short) 0),
  BZIP2((short) 1),
  LZMA_NEW((short) 3);

  CompressionAlgorithm(short value) {
    this.value = value;
  }

  public static CompressionAlgorithm fromValue(short value) {
    for (CompressionAlgorithm algo : CompressionAlgorithm.values()) {
      if (algo.value == value) {
        return algo;
      }
    }
    throw new IllegalArgumentException("Unknown value: " + value);
  }

  public short getValue() {
    return value;
  }

  private final short value;
}
