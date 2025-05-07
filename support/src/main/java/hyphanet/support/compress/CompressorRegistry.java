package hyphanet.support.compress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@SuppressWarnings("java:S6548")
public final class CompressorRegistry {
  private static final Logger logger = LoggerFactory.getLogger(CompressorRegistry.class);

  private CompressorRegistry() {
    // Use EnumMap for efficient enum key lookup
    Map<CompressorType, Compressor> compressors = new EnumMap<>(CompressorType.class);
    Map<String, CompressorType> names = new HashMap<>();
    Map<Short, CompressorType> ids = new HashMap<>();

    // Instantiate and register each compressor
    // Important: Ensure the concrete classes have public constructors if needed outside the package
    register(CompressorType.GZIP, new GzipCompressor(), compressors, names, ids);
    register(CompressorType.BZIP2, new Bzip2Compressor(), compressors, names, ids);
    register(CompressorType.LZMA_NEW, new NewLzmaCompressor(), compressors, names, ids);

    // Make maps and list immutable for safety
    this.compressorMap = Collections.unmodifiableMap(compressors);
    this.nameMap = Collections.unmodifiableMap(names);
    this.idMap = Collections.unmodifiableMap(ids);
  }

  /**
   * Gets the Singleton instance of the registry.
   *
   * @return The CompressorRegistry instance.
   */
  public static CompressorRegistry getInstance() {
    return SingletonHolder.INSTANCE;
  }

  /**
   * Gets the specific Compressor implementation for the given algorithm type.
   *
   * @param type The algorithm type.
   * @return The Compressor instance, or null if the type is not registered.
   */
  public Compressor getCompressor(CompressorType type) {
    return compressorMap.get(type);
  }

  /**
   * Gets the CompressorType enum constant by its registered name (case-insensitive).
   *
   * @param name The name (e.g., "GZIP", "bzip2").
   * @return The CompressorType enum, or null if not found.
   */
  public CompressorType getTypeByName(String name) {
    if (name == null) return null;
    return nameMap.get(name.toUpperCase());
  }

  /**
   * Gets the CompressorType enum constant by its metadata ID.
   *
   * @param id The short metadata ID.
   * @return The CompressorType enum, or null if not found.
   */
  public CompressorType getTypeByMetadataId(short id) {
    return idMap.get(id);
  }

  /**
   * Gets the Compressor implementation by its registered name (case-insensitive).
   *
   * @param name The name (e.g., "GZIP", "bzip2").
   * @return The Compressor instance, or null if not found.
   */
  public Compressor getCompressorByName(String name) {
    CompressorType type = getTypeByName(name);
    return (type != null) ? getCompressor(type) : null;
  }

  /**
   * Gets the Compressor implementation by its metadata ID.
   *
   * @param id The short metadata ID.
   * @return The Compressor instance, or null if not found.
   */
  public Compressor getCompressorByMetadataId(short id) {
    CompressorType type = getTypeByMetadataId(id);
    return (type != null) ? getCompressor(type) : null;
  }

  /**
   * Parses a compressor descriptor string into a list of CompressorType types. If the descriptor is
   * null or empty, returns the default list of algorithms. Handles deprecated LZMA logic (only
   * allowed if it's the *only* codec specified).
   *
   * @param compressorDescriptor The comma-separated string of names or IDs (e.g., "GZIP,BZIP2(1)").
   * @return A list of CompressorType types.
   * @throws InvalidCompressionCodecException If the descriptor contains unknown or duplicate
   *     identifiers.
   */
  public List<CompressorType> parseDescriptor(String compressorDescriptor)
      throws InvalidCompressionCodecException {
    if (compressorDescriptor == null || compressorDescriptor.trim().isEmpty()) {
      return List.of();
    }

    String[] codecs = compressorDescriptor.split(",");
    List<CompressorType> result = new ArrayList<>(codecs.length);

    for (String codecStr : codecs) {
      codecStr = codecStr.trim();
      if (codecStr.isEmpty()) continue; // Ignore empty parts

      CompressorType type = getTypeByName(codecStr);
      if (type == null) {
        try {
          // Try parsing as metadata ID
          short id = Short.parseShort(codecStr);
          type = getTypeByMetadataId(id);
        } catch (NumberFormatException _) {
          // Not a valid name or number
        }
      }

      if (type == null) {
        throw new InvalidCompressionCodecException(
            "Unknown compression codec identifier: '" + codecStr + "'");
      }

      if (result.contains(type)) {
        throw new InvalidCompressionCodecException(
            "Duplicate compression codec identifier: '" + codecStr + "'");
      }

      result.add(type);
    }

    return result;
  }

  /**
   * Parses a compressor descriptor string and returns the corresponding Compressor instances. If
   * the descriptor is null or empty, returns compressors for the default list of algorithms.
   *
   * @param compressorDescriptor The comma-separated string of names or IDs (e.g., "GZIP,BZIP2(1)").
   * @return A list of Compressor instances.
   * @throws InvalidCompressionCodecException If the descriptor contains unknown or duplicate
   *     identifiers.
   */
  public List<Compressor> getCompressorsFromDescriptorNoDefault(String compressorDescriptor)
      throws InvalidCompressionCodecException {
    List<CompressorType> types = parseDescriptor(compressorDescriptor);
    List<Compressor> compressors = new ArrayList<>(types.size());
    for (CompressorType type : types) {
      Compressor c = getCompressor(type);
      // Should not be null if parseDescriptor succeeded, but check defensively
      if (c != null) {
        compressors.add(c);
      } else {
        logger.error(
            "Could not find compressor instance for parsed type: {} - this indicates an internal error.",
            type);
      }
    }
    return compressors;
  }

  public List<Compressor> getCompressorsFromDescriptor(String compressorDescriptor)
      throws InvalidCompressionCodecException {
    var result = getCompressorsFromDescriptorNoDefault(compressorDescriptor);
    if (result.isEmpty()) {
      for (var compressorType : CompressorType.values()) {
        result.add(getCompressor(compressorType));
      }
    }
    return result;
  }

  /**
   * Generates a compressor descriptor string from a list of algorithm types.
   *
   * @param types The list of CompressorType types.
   * @return The descriptor string (e.g., "GZIP(0), BZIP2(1)").
   */
  public String generateDescriptor(List<CompressorType> types) {
    if (types == null || types.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (CompressorType type : types) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(type.getCompressorName());
      sb.append('(');
      sb.append(type.getMetadataId());
      sb.append(')');
      first = false;
    }
    return sb.toString();
  }

  private void register(
      CompressorType type,
      Compressor instance,
      Map<CompressorType, Compressor> compressors,
      Map<String, CompressorType> names,
      Map<Short, CompressorType> ids) {
    compressors.put(type, instance);
    // Store name lookup case-insensitively for robustness
    names.put(type.getCompressorName().toUpperCase(), type);
    ids.put(type.getMetadataId(), type);
  }

  private static final class SingletonHolder {
    static final CompressorRegistry INSTANCE = new CompressorRegistry();
  }

  private final Map<CompressorType, Compressor> compressorMap;
  private final Map<String, CompressorType> nameMap;
  private final Map<Short, CompressorType> idMap;
}
