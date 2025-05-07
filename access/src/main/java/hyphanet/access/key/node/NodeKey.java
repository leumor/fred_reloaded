package hyphanet.access.key.node;

import hyphanet.access.KeyDecodeException;
import hyphanet.access.KeyEncodeException;
import hyphanet.access.KeyType;
import hyphanet.access.key.*;
import hyphanet.support.compress.CompressionOutputSizeException;
import hyphanet.support.compress.CompressorRegistry;
import hyphanet.support.compress.InvalidCompressionCodecException;
import hyphanet.support.io.storage.bucket.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class NodeKey extends Key {
  public static class Compressed {
    public Compressed(byte[] finalData, CompressionAlgorithm compressionAlgorithm) {
      this.compressedData = finalData;
      this.compressionAlgorithm = compressionAlgorithm;
    }

    byte[] compressedData;
    CompressionAlgorithm compressionAlgorithm;
  }

  private static final Logger logger = LoggerFactory.getLogger(NodeKey.class);

  protected NodeKey(RoutingKey routingKey, CryptoAlgorithm cryptoAlgorithm) {
    super(routingKey, cryptoAlgorithm);
  }

  /** Compress data with optional pre-compression and size limits. */
  public static Compressed compress(
      Bucket sourceData,
      boolean dontCompress,
      CompressionAlgorithm precompressedAlgo,
      long originalLength,
      long maxBefore,
      int maxAfter,
      boolean shortPrefix,
      String descriptor)
      throws KeyEncodeException, IOException, InvalidCompressionCodecException {
    // Check uncompressed size
    if (sourceData.size() > maxBefore)
      throw new KeyEncodeException("Data exceeds max before-compression size");

    // Reserve prefix bytes
    int prefixSize = shortPrefix ? Short.BYTES : Integer.BYTES;
    int allowedAfter = maxAfter - prefixSize;

    // If precompressed or forced compression
    if (precompressedAlgo.getValue() >= 0 || !dontCompress) {
      // Handle precompressed input
      if (precompressedAlgo.getValue() >= 0) {
        return compressPrecompressed(
            sourceData, precompressedAlgo, originalLength, allowedAfter, prefixSize, shortPrefix);
      }
      // Attempt to compress
      Compressed result =
          tryAutoCompress(sourceData, descriptor, allowedAfter, prefixSize, shortPrefix);
      if (result != null) {
        return result;
      }
    }

    if (sourceData.size() > allowedAfter + prefixSize) {
      throw new KeyEncodeException(
          KeyType.CHK,
          String.format("Data too big: %d > %d", sourceData.size(), allowedAfter + prefixSize));
    }
    return new Compressed(BucketTools.toByteArray(sourceData), CompressionAlgorithm.NO_COMP);
  }

  public static Bucket decompress(
      boolean compressed,
      byte[] in,
      int length,
      BucketFactory factory,
      long maxLength,
      CompressionAlgorithm algo,
      boolean shortPrefix)
      throws KeyDecodeException, IOException {
    if (maxLength < 0) {
      throw new IllegalArgumentException("maxLength must be >= 0");
    }
    if (in.length < length) {
      throw new IndexOutOfBoundsException();
    }
    if (!compressed) {
      return BucketTools.makeImmutableBucket(factory, in, length);
    }
    int prefixSize = shortPrefix ? Short.BYTES : Integer.BYTES;
    if (length < prefixSize + 1) {
      throw new KeyDecodeException(KeyType.CHK, "No data to decompress");
    }
    ByteBuffer buf = ByteBuffer.wrap(in, 0, length);
    long origLen = shortPrefix ? buf.getShort() & 0xFFFF : buf.getInt() & 0xFFFFFFFFL;
    if (origLen > maxLength) {
      throw new TooBigException("Decompressed length exceeds max");
    }
    byte[] payload = new byte[length - prefixSize];
    buf.get(payload);
    var decomp = CompressorRegistry.getInstance().getCompressorByMetadataId(algo.getValue());
    if (decomp == null) {
      throw new KeyDecodeException(KeyType.CHK, "Unknown codec " + algo);
    }
    try (var inBucket = new SimpleReadOnlyArrayBucket(payload, 0, payload.length);
        var outBucket = factory.makeBucket(maxLength);
        var inStream = inBucket.getInputStream();
        var outStream = outBucket.getOutputStream()) {
      decomp.decompress(inStream, outStream, maxLength, -1);
      return outBucket;
    } catch (CompressionOutputSizeException e) {
      throw new TooBigException("Output too large");
    }
  }

  public byte[] getRoutingKeyBytes() {
    return getRoutingKey().bytes();
  }

  /**
   * Get key type
   *
   * <ul>
   *   <li>High 8 bit (<tt>(type >> 8) & 0xFF</tt>) is the base type ({@link NodeChk#BASE_TYPE} or
   *       {@link NodeChk#BASE_TYPE}).
   *   <li>Low 8 bit (<tt>type & 0xFF</tt>) is the crypto algorithm. (Currently only {@link
   *       CryptoAlgorithm#ALGO_AES_PCFB_256_SHA256} is supported).
   * </ul>
   */
  public abstract short getType();

  public abstract byte[] getFullKeyBytes();

  private static Compressed compressPrecompressed(
      Bucket src,
      CompressionAlgorithm algo,
      long origLen,
      int maxSize,
      int prefixSize,
      boolean shortPrefix)
      throws IOException, TooBigException {
    if (src.size() > maxSize) {
      throw new TooBigException("Precompressed data too big");
    }
    byte[] raw = BucketTools.toByteArray(src);
    return buildWithPrefix(raw, origLen, algo, prefixSize, shortPrefix);
  }

  private static Compressed tryAutoCompress(
      Bucket src, String descriptor, int maxSize, int prefixSize, boolean shortPrefix)
      throws InvalidCompressionCodecException, IOException {
    CompressorRegistry reg = CompressorRegistry.getInstance();
    for (var type : reg.parseDescriptor(descriptor)) {
      try {
        ArrayBucket compressed =
            (ArrayBucket)
                reg.getCompressor(type)
                    .compress(src, new ArrayBucketFactory(), Long.MAX_VALUE, maxSize);
        if (compressed.size() <= maxSize) {
          byte[] raw = BucketTools.toByteArray(compressed);
          return buildWithPrefix(
              raw,
              src.size(),
              CompressionAlgorithm.fromValue(type.getMetadataId()),
              prefixSize,
              shortPrefix);
        }
      } catch (CompressionOutputSizeException _) {
        // skip oversized output
      }
    }
    return null;
  }

  private static Compressed buildWithPrefix(
      byte[] raw, long origLen, CompressionAlgorithm algo, int prefixSize, boolean shortPrefix) {
    ByteBuffer buf = ByteBuffer.allocate(prefixSize + raw.length);
    if (shortPrefix) {
      buf.putShort((short) raw.length);
    } else {
      buf.putInt((int) origLen);
    }
    buf.put(raw);
    return new Compressed(buf.array(), algo);
  }
}
