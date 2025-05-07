package hyphanet.support.compress;

public enum CompressorType {
  GZIP("GZIP", (short) 0),
  BZIP2("BZIP2", (short) 1),
  LZMA_NEW("LZMA_NEW", (short) 3);

  // Cache values for efficient iteration
  private static final CompressorType[] VALUES = values();

  CompressorType(String compressorName, short metadataId) {
    this.compressorName = compressorName;
    this.metadataId = metadataId;
  }

  /** Returns all defined compressor types, including deprecated ones. */
  public static CompressorType[] allValues() {
    // Return a clone to prevent external modification
    return VALUES.clone();
  }

  public static CompressorType findByName(String name) {
    if (name == null) return null;
    for (CompressorType alg : VALUES) {
      if (alg.compressorName.equalsIgnoreCase(name)) { // Often useful to be case-insensitive
        return alg;
      }
    }
    return null;
  }

  public static CompressorType findByMetadataID(short id) {
    for (CompressorType alg : VALUES) {
      if (alg.metadataId == id) {
        return alg;
      }
    }
    return null;
  }

  public String getCompressorName() {
    return compressorName;
  }

  public short getMetadataId() {
    return metadataId;
  }

  private final String compressorName;
  private final short metadataId;
}
