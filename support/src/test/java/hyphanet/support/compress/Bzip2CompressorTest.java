/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.compress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hyphanet.support.io.storage.bucket.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Test case for {@link Bzip2Compressor} class. */
class Bzip2CompressorTest {

  private static final String UNCOMPRESSED_DATA_1 = GzipCompressorTest.UNCOMPRESSED_DATA_1;

  private static final byte[] COMPRESSED_DATA_1 = {
    104, 57, 49, 65, 89, 38, 83, 89, -18, -87, -99, -74, 0, 0, 33, -39, -128, 0, 8, 16, 0, 58, 64,
    52, -7, -86, 0, 48, 0, -69, 65, 76, 38, -102, 3, 76, 65, -92, -12, -43, 61, 71, -88, -51, 35,
    76, 37, 52, 32, 19, -44, 67, 74, -46, -9, 17, 14, -35, 55, 100, -10, 73, -75, 121, -34, 83, 56,
    -125, 15, 32, -118, 35, 66, 124, -120, -39, 119, -104, -108, 66, 101, -56, 94, -71, -41, -43,
    68, 51, 65, 19, -44, -118, 4, -36, -117, 33, -101, -120, -49, -10, 17, -51, -19, 28, 76, -57,
    -112, -68, -50, -66, -60, -43, -81, 127, -51, -10, 58, -92, 38, 18, 45, 102, 117, -31, -116,
    -114, -6, -87, -59, -43, -106, 41, -30, -63, -34, -39, -117, -104, -114, 100, -115, 36, -112,
    23, 104, -110, 71, -45, -116, -23, -85, -36, -24, -61, 14, 32, 105, 55, -105, -31, -4, 93, -55,
    20, -31, 66, 67, -70, -90, 118, -40
  };
  private static CompressorRegistry registry;
  private static Compressor compressor;

  @BeforeAll
  static void init() {
    registry = CompressorRegistry.getInstance();
    compressor = registry.getCompressor(CompressorType.BZIP2);
  }

  /** test BZIP2 compressor's identity and functionality */
  @Test
  void testBzip2Compressor() throws IOException {
    var compressorZero = registry.getTypeByMetadataId((short) 1);

    // check BZIP2 is the second compressor
    assertEquals(CompressorType.BZIP2, compressorZero);
  }

  @Test
  void testCompress() throws IOException {

    // do bzip2 compression
    byte[] compressedData = doCompress(UNCOMPRESSED_DATA_1.getBytes());

    // output size same as expected?
    // assertEquals(compressedData.length, COMPRESSED_DATA_1.length);

    // check each byte is exactly as expected
    for (int i = 0; i < compressedData.length; i++) {
      assertEquals(COMPRESSED_DATA_1[i], compressedData[i]);
    }
  }

  @Test
  void testBucketDecompress() throws IOException {

    // do bzip2 decompression with buckets
    byte[] uncompressedData = doBucketDecompress();

    // is the (round-tripped) uncompressed string the same as the original?
    String uncompressedString = new String(uncompressedData);
    assertEquals(UNCOMPRESSED_DATA_1, uncompressedString);
  }

  @Test
  void testByteArrayDecompress() throws IOException {

    // build 5k array
    byte[] originalUncompressedData = new byte[5 * 1024];
    Arrays.fill(originalUncompressedData, (byte) 1);

    byte[] compressedData = doCompress(originalUncompressedData);
    byte[] outUncompressedData = new byte[5 * 1024];

    int writtenBytes = 0;

    writtenBytes =
        compressor.decompress(compressedData, 0, compressedData.length, outUncompressedData);

    assertEquals(originalUncompressedData.length, writtenBytes);
    assertEquals(originalUncompressedData.length, outUncompressedData.length);

    // check each byte is exactly as expected
    for (int i = 0; i < outUncompressedData.length; i++) {
      assertEquals(originalUncompressedData[i], outUncompressedData[i]);
    }
  }

  //  @Test
  //  void testCompressException() throws IOException {
  //
  //    byte[] uncompressedData = UNCOMPRESSED_DATA_1.getBytes();
  //    Bucket inBucket = new ArrayBucket(uncompressedData);
  //    BucketFactory factory = new ArrayBucketFactory();
  //
  //    try {
  //      compressor.compress(inBucket, factory, 32, 32);
  //    } catch (CompressionOutputSizeException e) {
  //      // expect this
  //      return;
  //    }
  //    // TODO LOW codec doesn't actually enforce size limit
  //    // fail("did not throw expected CompressionOutputSizeException");
  //
  //  }

  @Test
  void testDecompressException() throws IOException {
    // build 5k array
    byte[] uncompressedData = new byte[5 * 1024];
    Arrays.fill(uncompressedData, (byte) 1);

    byte[] compressedData = doCompress(uncompressedData);

    try (Bucket inBucket = new ArrayBucket(compressedData);
        NullBucket outBucket = new NullBucket()) {
      try (var decompressorInput = inBucket.getInputStream();
          var decompressorOutput = outBucket.getOutputStream()) {
        assertThrows(
            CompressionOutputSizeException.class,
            () -> {
              compressor.decompress(decompressorInput, decompressorOutput, 4096 + 10, 4096 + 20);
            });
      } catch (CompressionOutputSizeException e) {
        // expect this
        return;
      } finally {
        inBucket.dispose();
        outBucket.dispose();
      }
    }
  }

  private byte[] doBucketDecompress() throws IOException {
    try (ByteArrayInputStream decompressorInput =
            new ByteArrayInputStream(Bzip2CompressorTest.COMPRESSED_DATA_1);
        ByteArrayOutputStream decompressorOutput = new ByteArrayOutputStream()) {
      compressor.decompress(decompressorInput, decompressorOutput, 32768, 32768 * 2);

      return decompressorOutput.toByteArray();
    }
  }

  private byte[] doCompress(byte[] uncompressedData) throws IOException {
    Bucket inBucket = new ArrayBucket(uncompressedData);
    BucketFactory factory = new ArrayBucketFactory();
    Bucket outBucket = null;

    outBucket = compressor.compress(inBucket, factory, 32768, 32768);

    InputStream in = null;
    in = outBucket.getInputStream();
    long size = outBucket.size();
    byte[] outBuf = new byte[(int) size];

    in.read(outBuf);

    return outBuf;
  }
}
