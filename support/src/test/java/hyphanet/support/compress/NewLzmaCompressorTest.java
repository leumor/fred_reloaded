/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.compress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hyphanet.support.io.storage.bucket.ArrayBucket;
import hyphanet.support.io.storage.bucket.ArrayBucketFactory;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Test case for {@link NewLzmaCompressor} class. */
class NewLzmaCompressorTest {

  private static final String UNCOMPRESSED_DATA_1 = GzipCompressorTest.UNCOMPRESSED_DATA_1;
  private static CompressorRegistry registry;
  private static Compressor compressor;

  @BeforeAll
  static void init() {
    registry = CompressorRegistry.getInstance();
    compressor = registry.getCompressor(CompressorType.GZIP);
  }

  /** test BZIP2 compressor's identity and functionality */
  @Test
  void testNewLzmaCompressor() throws IOException {
    var compressorZero = registry.getTypeByMetadataId((short) 3);

    // check BZIP2 is the second compressor
    assertEquals(CompressorType.LZMA_NEW, compressorZero);
  }

  // FIXME add exact decompression check.

  //	public void testCompress() throws IOException {
  //
  //		// do bzip2 compression
  //		byte[] compressedData = doCompress(UNCOMPRESSED_DATA_1.getBytes());
  //
  //		// output size same as expected?
  //		//assertEquals(compressedData.length, COMPRESSED_DATA_1.length);
  //
  //		// check each byte is exactly as expected
  //		for (int i = 0; i < compressedData.length; i++) {
  //			assertEquals(COMPRESSED_DATA_1[i], compressedData[i]);
  //		}
  //	}
  //
  //	public void testBucketDecompress() throws IOException {
  //
  //		byte[] compressedData = COMPRESSED_DATA_1;
  //
  //		// do bzip2 decompression with buckets
  //		byte[] uncompressedData = doBucketDecompress(compressedData);
  //
  //		// is the (round-tripped) uncompressed string the same as the original?
  //		String uncompressedString = new String(uncompressedData);
  //		assertEquals(uncompressedString, UNCOMPRESSED_DATA_1);
  //	}
  //
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

    assertEquals(writtenBytes, originalUncompressedData.length);
    assertEquals(originalUncompressedData.length, outUncompressedData.length);

    // check each byte is exactly as expected
    for (int i = 0; i < outUncompressedData.length; i++) {
      assertEquals(originalUncompressedData[i], outUncompressedData[i]);
    }
  }

  @Test
  void testRandomByteArrayDecompress() throws IOException {

    Random random = new Random(1234);

    for (int rounds = 0; rounds < 100; rounds++) {
      int scale = random.nextInt(19) + 1;
      int size = random.nextInt(1 << scale);

      if (size == 0) continue;

      // build 5k array
      byte[] originalUncompressedData = new byte[size];
      random.nextBytes(originalUncompressedData);

      byte[] compressedData = doCompress(originalUncompressedData);
      byte[] outUncompressedData = new byte[size];

      int writtenBytes = 0;

      writtenBytes =
          compressor.decompress(compressedData, 0, compressedData.length, outUncompressedData);

      assertEquals(writtenBytes, originalUncompressedData.length);
      assertEquals(originalUncompressedData.length, outUncompressedData.length);

      // check each byte is exactly as expected
      for (int i = 0; i < outUncompressedData.length; i++) {
        assertEquals(originalUncompressedData[i], outUncompressedData[i]);
      }
    }
  }

  //	@Test
  //	 void testCompressException() throws IOException {
  //
  //		byte[] uncompressedData = UNCOMPRESSED_DATA_1.getBytes();
  //		Bucket inBucket = new ArrayBucket(uncompressedData);
  //		BucketFactory factory = new ArrayBucketFactory();
  //
  //		try {
  //			Compressor.COMPRESSOR_TYPE.LZMA_NEW.compress(inBucket, factory, 32, 32);
  //		} catch (CompressionOutputSizeException e) {
  //			// expect this
  //			return;
  //		}
  //		// TODO LOW codec doesn't actually enforce size limit
  //		//fail("did not throw expected CompressionOutputSizeException");
  //	}

  //	@Test
  //	void testDecompressException() throws IOException {
  //
  //		// build 5k array
  //		byte[] uncompressedData = new byte[5 * 1024];
  //    Arrays.fill(uncompressedData, (byte) 1);
  //
  //		byte[] compressedData = doCompress(uncompressedData);
  //
  //		Bucket inBucket = new ArrayBucket(compressedData);
  //		NullBucket outBucket = new NullBucket();
  //
  //		try (
  //			var decompressorInput = inBucket.getInputStream();
  //		var decompressorOutput = outBucket.getOutputStream()
  //
  //			) {
  //			compressor.decompress(decompressorInput, decompressorOutput, 4096 + 10, 4096 + 20);
  //			decompressorInput.close();
  //			decompressorOutput.close();
  //		} catch (CompressionOutputSizeException e) {
  //			// expect this
  //			return;
  //		} finally {
  //			Closer.close(decompressorInput);
  //			Closer.close(decompressorOutput);
  //			inBucket.free();
  //			outBucket.free();
  //		}
  //		// TODO LOW codec doesn't actually enforce size limit
  //		//fail("did not throw expected CompressionOutputSizeException");
  //	}

  private byte[] doCompress(byte[] uncompressedData) throws IOException {
    Bucket inBucket = new ArrayBucket(uncompressedData);
    BucketFactory factory = new ArrayBucketFactory();
    Bucket outBucket = null;

    outBucket =
        compressor.compress(
            inBucket, factory, uncompressedData.length, uncompressedData.length * 2L + 64);

    InputStream in = null;
    in = outBucket.getInputStream();
    long size = outBucket.size();
    byte[] outBuf = new byte[(int) size];

    in.read(outBuf);

    return outBuf;
  }
}
