package hyphanet.support.io.storage.rab;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.storage.TempStorageManager;
import hyphanet.support.io.storage.bucket.RandomAccessBucket;
import hyphanet.support.io.storage.bucket.TempBucket;
import hyphanet.support.io.storage.bucket.TempFileBucket;
import hyphanet.support.io.storage.bucket.wrapper.EncryptedBucket;
import hyphanet.support.io.storage.bucket.wrapper.PaddedRandomAccessBucket;
import hyphanet.support.io.util.FileSystem;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

abstract class TempRabTestBase extends RabTestBase {

  static final MasterSecret secret = new MasterSecret();
  private static final int[] TEST_LIST =
      new int[] {0, 1, 32, 64, 32768, 1024 * 1024, 1024 * 1024 + 1};
  private static final int[] TEST_LIST_NOT_MIGRATED = new int[] {1, 32, 64, 1024, 2048, 4095};

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  TempRabTestBase() {
    super(TEST_LIST);
  }

  abstract boolean enableCrypto();

  @BeforeEach
  void setUp() throws IOException {
    FilenameGenerator fg = new FilenameGenerator(weakPRNG, true, path, "temp-raf-test-");
    manager = new TempStorageManager(exec, fg, 4096, 65536, 1024 * 1024 * 2, false, secret);
    manager.setEncrypt(enableCrypto());
    assertEquals(0, manager.getRamTracker().getRamBytesInUse());
    FileSystem.removeAll(path);
    Files.createDirectories(path);
    assertTrue(Files.exists(path) && Files.isDirectory(path));
  }

  @AfterEach
  void tearDown() throws IOException {
    assertEquals(0, manager.getRamTracker().getRamBytesInUse());
    // Everything should have been free()'ed.
    try (var directoryStream = Files.newDirectoryStream(path)) {
      assertFalse(directoryStream.iterator().hasNext());
    }
    FileSystem.removeAll(path);
  }

  @Test
  void testArrayMigration() throws IOException {
    Random r = new Random(21162506);
    for (int size : TEST_LIST_NOT_MIGRATED) innerTestArrayMigration(size, r);
  }

  @Test
  void testBucketToRabWhileArray() throws IOException {
    int len = 4095;
    Random r = new Random(21162101);
    TempBucket bucket = manager.makeBucket(1024);
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    OutputStream os = bucket.getOutputStream();
    os.write(buf.clone());
    os.close();
    assertTrue(bucket.isRamBucket());
    assertEquals(len, bucket.size());
    TempRab rab = bucket.toRandomAccessBuffer();
    bucket.getInputStream().close(); // Can read.
    assertThrows(IOException.class, bucket::getOutputStream); // Cannot write.
    assertEquals(len, rab.size());
    assertFalse(rab.hasMigrated());
    checkArrayInner(buf, rab, len, r);
    // Now migrate to disk.
    rab.migrateToDisk();
    Path underlyingPath = ((PooledFileRab) rab.getUnderlying()).getPath();
    assertTrue(Files.exists(underlyingPath));
    assertEquals(len, Files.size(underlyingPath));
    assertTrue(rab.hasMigrated());
    assertEquals(0, manager.getRamTracker().getRamBytesInUse());
    checkArrayInner(buf, rab, len, r);
    checkBucket(bucket, buf);
    rab.close();
    rab.dispose();
    assertFalse(Files.exists(underlyingPath));
  }

  @Test
  void testBucketToRabCallTwiceArray() throws IOException {
    int len = 4095;
    Random r = new Random(21162101);
    TempBucket bucket = manager.makeBucket(1024);
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    OutputStream os = bucket.getOutputStream();
    os.write(buf.clone());
    os.close();
    assertTrue(bucket.isRamBucket());
    assertEquals(len, bucket.size());
    TempRab rab = bucket.toRandomAccessBuffer();
    assertNotNull(rab);
    rab = bucket.toRandomAccessBuffer();
    assertNotNull(rab);
    rab.close();
    rab.dispose();
  }

  @Test
  void testBucketToRabCallTwiceFile() throws IOException {
    int len = 4095;
    Random r = new Random(21162101);
    TempBucket bucket = manager.makeBucket(1024);
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    OutputStream os = bucket.getOutputStream();
    os.write(buf.clone());
    os.close();
    assertTrue(bucket.isRamBucket());
    assertEquals(len, bucket.size());
    assertTrue(bucket.migrateToDisk());
    TempRab rab = bucket.toRandomAccessBuffer();
    assertNotNull(rab);
    rab = bucket.toRandomAccessBuffer();
    assertNotNull(rab);
    rab.close();
    rab.dispose();
  }

  @Test
  void testBucketToRabFreeBucketWhileArray() throws IOException {
    int len = 4095;
    Random r = new Random(21162101);
    TempBucket bucket = manager.makeBucket(1024);
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    OutputStream os = bucket.getOutputStream();
    os.write(buf.clone());
    os.close();
    assertTrue(bucket.isRamBucket());
    assertEquals(len, bucket.size());
    bucket.getInputStream().close();
    bucket.dispose();
    assertThrows(IOException.class, bucket::toRandomAccessBuffer);
  }

  @Test
  void testBucketToRabFreeWhileArray() throws IOException {
    int len = 4095;
    Random r = new Random(21162101);
    TempBucket bucket = manager.makeBucket(1024);
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    OutputStream os = bucket.getOutputStream();
    os.write(buf.clone());
    os.close();
    assertTrue(bucket.isRamBucket());
    assertEquals(len, bucket.size());
    bucket.getInputStream().close();
    TempRab rab = bucket.toRandomAccessBuffer();
    bucket.dispose();
    assertThrows(IOException.class, () -> rab.pread(0, new byte[len], 0, buf.length));
    assertThrows(IOException.class, bucket::getInputStream);
  }

  @Test
  void testBucketToRabFreeWhileFile() throws IOException {
    int len = 4095;
    Random r = new Random(21162101);
    TempBucket bucket = manager.makeBucket(1024);
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    OutputStream os = bucket.getOutputStream();
    os.write(buf.clone());
    os.close();
    assertTrue(bucket.isRamBucket());
    assertEquals(len, bucket.size());
    bucket.getInputStream().close();
    TempRab rab = bucket.toRandomAccessBuffer();
    assertTrue(rab.migrateToDisk());
    assertFalse(rab.migrateToDisk());
    assertFalse(bucket.migrateToDisk());
    assertTrue(rab.hasMigrated());
    Path underlyingPath = ((PooledFileRab) rab.getUnderlying()).getPath();
    assertTrue(Files.exists(underlyingPath));
    bucket.dispose();
    assertFalse(Files.exists(underlyingPath));
    assertThrows(IOException.class, () -> rab.pread(0, new byte[len], 0, buf.length));
    assertThrows(IOException.class, bucket::getInputStream);
  }

  @Test
  void testBucketToRabFreeWhileFileFreeRAF() throws IOException {
    int len = 4095;
    Random r = new Random(21162101);
    TempBucket bucket = manager.makeBucket(1024);
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    OutputStream os = bucket.getOutputStream();
    os.write(buf.clone());
    os.close();
    assertTrue(bucket.isRamBucket());
    assertEquals(len, bucket.size());
    bucket.getInputStream().close();
    TempRab rab = bucket.toRandomAccessBuffer();
    rab.migrateToDisk();
    assertTrue(rab.hasMigrated());
    Path underlyingPath = ((PooledFileRab) rab.getUnderlying()).getPath();
    assertTrue(Files.exists(underlyingPath));
    rab.dispose();
    assertFalse(Files.exists(underlyingPath));
    assertThrows(IOException.class, () -> rab.pread(0, new byte[len], 0, buf.length));
    InputStream is = bucket.getInputStream();
    // Tricky to make it fail on getInputStream()
    assertThrows(IOException.class, is::read);
  }

  @Test
  void testBucketToRabFreeWhileFileMigrateFirst() throws IOException {
    int len = 4095;
    Random r = new Random(21162101);
    TempBucket bucket = manager.makeBucket(1024);
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    OutputStream os = bucket.getOutputStream();
    os.write(buf.clone());
    os.close();
    assertTrue(bucket.isRamBucket());
    assertEquals(len, bucket.size());
    bucket.getInputStream().close();
    bucket.migrateToDisk();
    Path underlyingPath = getPath(bucket);
    assertTrue(Files.exists(underlyingPath));
    TempRab rab = bucket.toRandomAccessBuffer();
    assertTrue(rab.hasMigrated());
    bucket.dispose();
    assertFalse(Files.exists(underlyingPath));
    assertThrows(IOException.class, () -> rab.pread(0, new byte[len], 0, buf.length));
    assertThrows(IOException.class, bucket::getInputStream);
  }

  @Test
  void testBucketToRabWhileFile() throws IOException {
    int len = 4095;
    Random r = new Random(21162101);
    TempBucket bucket = manager.makeBucket(1024);
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    OutputStream os = bucket.getOutputStream();
    os.write(buf.clone());
    os.close();
    assertTrue(bucket.isRamBucket());
    assertEquals(len, bucket.size());
    // Migrate to disk
    bucket.migrateToDisk();
    assertFalse(bucket.isRamBucket());
    Path underlyingPath = getPath(bucket);
    assertTrue(Files.exists(underlyingPath));
    if (enableCrypto()) {
      assertEquals(8192, Files.size(underlyingPath));
    } else {
      assertEquals(4095, Files.size(underlyingPath));
    }
    TempRab rab = bucket.toRandomAccessBuffer();
    assertTrue(Files.exists(underlyingPath));
    if (enableCrypto()) {
      assertEquals(8192, Files.size(underlyingPath));
    } else {
      assertEquals(4095, Files.size(underlyingPath));
    }
    assertEquals(len, rab.size());
    checkArrayInner(buf, rab, len, r);
    assertEquals(0, manager.getRamTracker().getRamBytesInUse());
    checkArrayInner(buf, rab, len, r);
    rab.close();
    rab.dispose();
    assertFalse(Files.exists(underlyingPath));
  }

  @Test
  void testBucketToRabFailure() throws IOException {
    int len = 4095;
    Random r = new Random(21162101);
    TempBucket bucket = manager.makeBucket(1024);
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    OutputStream os = bucket.getOutputStream();
    os.write(buf.clone());
    assertTrue(bucket.isRamBucket());
    assertThrows(IOException.class, bucket::toRandomAccessBuffer);
    os.close();
    InputStream is = bucket.getInputStream();
    assertThrows(IOException.class, bucket::toRandomAccessBuffer);
    is.close();
    TempRab rab = bucket.toRandomAccessBuffer();
    assertThrows(IOException.class, bucket::getOutputStream);
    checkBucket(bucket, buf);
    rab.close();
    rab.dispose();
  }

  @Override
  protected Rab construct(long size) throws IOException {
    return manager.makeRab(size);
  }

  /**
   * Create an array, fill it with random numbers, write it sequentially to the RandomAccessBuffer,
   * then read randomly and compare.
   */
  protected void innerTestArrayMigration(int len, Random r) throws IOException {
    if (len == 0) return;
    byte[] buf = new byte[len];
    r.nextBytes(buf);
    Rab rab = construct(len);
    TempRab t = (TempRab) rab;
    assertFalse(t.hasMigrated());
    assertEquals(len, manager.getRamTracker().getRamBytesInUse());
    t.migrateToDisk();
    assertTrue(t.hasMigrated());
    assertEquals(0, manager.getRamTracker().getRamBytesInUse());
    rab.pwrite(0L, buf, 0, buf.length);
    checkArrayInner(buf, rab, len, r);
    rab.close();
    rab.dispose();
  }

  private Path getPath(TempBucket bucket) {
    if (!this.enableCrypto()) {
      return ((TempFileBucket) (bucket.getUnderlying())).getPath();
    } else {
      EncryptedBucket erab = (EncryptedBucket) bucket.getUnderlying();
      RandomAccessBucket b = erab.getUnderlying();
      if (b instanceof PaddedRandomAccessBucket) {
        b = ((PaddedRandomAccessBucket) b).getUnderlying();
      }
      return ((TempFileBucket) b).getPath();
    }
  }

  private void checkBucket(TempBucket bucket, byte[] buf) throws IOException {
    DataInputStream dis = new DataInputStream(bucket.getInputStream());
    byte[] cbuf = new byte[buf.length];
    dis.readFully(cbuf);
    assertArrayEquals(buf, cbuf);
  }

  private void checkArrayInner(byte[] buf, Rab rab, int len, Random r) throws IOException {
    for (int i = 0; i < 100; i++) {
      int end = len == 1 ? 1 : r.nextInt(len) + 1;
      int start = r.nextInt(end);
      checkArraySectionEqualsReadData(buf, rab, start, end, true);
    }
    checkArraySectionEqualsReadData(buf, rab, 0, len, true);
    if (len > 1) checkArraySectionEqualsReadData(buf, rab, 1, len - 1, true);
  }

  private final Random weakPRNG = new Random(12340);
  private final ExecutorService exec = Executors.newSingleThreadExecutor();
  private final Path path = Path.of("temp-bucket-raf-test");
  private TempStorageManager manager;
}
