/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket;

import static org.junit.jupiter.api.Assertions.*;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.TempStorageManager;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.Security;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class TempBucketTest {

  static final MasterSecret secret = new MasterSecret();
  private static final long MIN_DISK_SPACE = 2 * 1024 * 1024;

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  // Private because we only use it as a base class for the actual tests.
  private static class RealTempBucketTest_ extends BucketTestBase {
    public RealTempBucketTest_(int maxRamSize, int maxTotalRamSize, boolean encrypted)
        throws IOException {
      Random weakPRNG = new Random();
      FilenameGenerator fg = new FilenameGenerator(weakPRNG, false, null, "junit");
      ExecutorService exec = Executors.newSingleThreadExecutor();
      tsm =
          new TempStorageManager(
              exec, fg, maxRamSize, maxTotalRamSize, MIN_DISK_SPACE, encrypted, secret);

      canOverwrite = false;
    }

    @Override
    protected void freeBucket(Bucket bucket) {
      bucket.dispose();
    }

    @Override
    protected Bucket makeBucket(long size) throws IOException {
      return tsm.makeBucket(1); // TempBucket allow resize
    }

    private final TempStorageManager tsm;
  }

  @Nested
  class RealTempBucketTest_64_128_F extends RealTempBucketTest_ {
    public RealTempBucketTest_64_128_F() throws IOException {
      super(64, 128, false);
    }
  }

  @Nested
  class RealTempBucketTest_64k_128k_F extends RealTempBucketTest_ {
    public RealTempBucketTest_64k_128k_F() throws IOException {
      super(64 * 1024, 128 * 1024, false);
    }
  }

  @Nested
  class RealTempBucketTest_8_16_T extends RealTempBucketTest_ {
    public RealTempBucketTest_8_16_T() throws IOException {
      super(8, 16, true);
    }
  }

  @Nested
  class RealTempBucketTest_64k_128k_T extends RealTempBucketTest_ {
    public RealTempBucketTest_64k_128k_T() throws IOException {
      super(64 * 1024, 128 * 1024, true);
    }
  }

  @Nested
  class RealTempBucketTest_8_16_F extends RealTempBucketTest_ {
    public RealTempBucketTest_8_16_F() throws IOException {
      super(8, 16, false);
    }
  }

  @Nested
  class TempBucketMigrationTest {
    public TempBucketMigrationTest() throws IOException {
      Random weakPRNG = new Random(12340);
      fg = new FilenameGenerator(weakPRNG, false, null, "junit");
    }

    @Test
    void testRamLimitCreate() throws IOException {
      TempStorageManager tsm =
          new TempStorageManager(exec, fg, 16, 128, MIN_DISK_SPACE, false, secret);

      int maxRamBucket = 128 / 16;

      // create excess maxTotalRamSize, last one should be on disk
      TempBucket[] b = new TempBucket[maxRamBucket + 1];
      for (int i = 0; i < maxRamBucket + 1; i++) {
        var bucket = tsm.makeBucket(16);
        b[i] = bucket;

        OutputStream os = b[i].getOutputStream();
        os.write(new byte[16]);
        os.close();
      }

      try {
        assertTrue(b[0].isRamStorage());
        assertFalse(b[maxRamBucket].isRamStorage());

        // Free some, reused the space
        b[0].dispose();
        b[maxRamBucket].dispose();

        try (var bucket1 = tsm.makeBucket(8);
            var bucket2 = tsm.makeBucket(8)) {
          b[0] = bucket1;
          b[maxRamBucket] = bucket2;
          assertTrue(b[0].isRamStorage());
          assertTrue(b[maxRamBucket].isRamStorage());
        }
      } finally {
        for (Bucket bb : b) bb.dispose();
      }
    }

    @Test
    void testWriteExcessConversionFactor() throws IOException {
      TempStorageManager tsm =
          new TempStorageManager(exec, fg, 16, 128, MIN_DISK_SPACE, false, secret);

      var b = tsm.makeBucket(16);
      try {
        assertTrue(b.isRamStorage());

        OutputStream os = b.getOutputStreamUnbuffered();

        os.write(new byte[16]);
        assertTrue(b.isRamStorage());

        for (int i = 0; i < TempStorageManager.RAMSTORAGE_CONVERSION_FACTOR - 1; i++) {
          os.write(new byte[16]);
        }
        assertFalse(b.isRamStorage());
      } finally {
        b.close();
        b.dispose();
      }
    }

    @Test
    void testWriteExcessLimit() throws IOException {
      TempStorageManager tsm =
          new TempStorageManager(exec, fg, 16, 17, MIN_DISK_SPACE, false, secret);

      TempBucket b = tsm.makeBucket(16);
      try {
        assertTrue(b.isRamStorage());

        OutputStream os = b.getOutputStreamUnbuffered();

        os.write(new byte[16]);
        assertTrue(b.isRamStorage());

        os.write(new byte[2]);
        assertFalse(b.isRamStorage());
      } finally {
        b.close();
        b.dispose();
      }
    }

    // This CAN happen due to memory pressure.
    @Test
    void testConversionWhileReading() throws IOException {
      TempStorageManager tsm =
          new TempStorageManager(exec, fg, 1024, 65536, MIN_DISK_SPACE, false, secret);

      try (var bucket = tsm.makeBucket(64);
          var os = bucket.getOutputStreamUnbuffered();
          var is = bucket.getInputStream()) {
        os.write(new byte[16]);

        bucket.migrateToDisk();
        byte[] readTo = new byte[16];
        assertEquals(16, is.read(readTo, 0, 16));

        for (byte b : readTo) {
          assertEquals(0, b);
        }
      }
    }

    // Do a bigger read, verify contents.
    @Test
    void testBigConversionWhileReading() throws IOException {
      TempStorageManager tsm =
          new TempStorageManager(exec, fg, 4096, 65536, MIN_DISK_SPACE, false, secret);

      try (var bucket = tsm.makeBucket(2048);
          var os = bucket.getOutputStreamUnbuffered();
          var is = bucket.getInputStream()) {

        byte[] data = new byte[2048];
        new Random(89).nextBytes(data);
        os.write(data);

        bucket.migrateToDisk();
        byte[] readTo = new byte[2048];
        new DataInputStream(is).readFully(readTo);
        for (int i = 0; i < readTo.length; i++) {
          assertEquals(readTo[i], data[i]);
        }
      }
    }

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final FilenameGenerator fg;
  }
}
