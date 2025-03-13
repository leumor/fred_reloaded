package hyphanet.support.io.storage.rab;

import static org.junit.jupiter.api.Assertions.*;

import hyphanet.support.io.util.FileSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PooledFileRabTest extends RabTestBase {

  private static final int[] TEST_LIST =
      new int[] {0, 1, 32, 64, 32768, 1024 * 1024, 1024 * 1024 + 1};

  PooledFileRabTest() {
    super(TEST_LIST);
  }

  /** Test that locking and unlocking do something */
  @Test
  void testLock() throws IOException {
    int sz = 1024;
    fds.setMaxFDs(1);
    assertEquals(0, fds.getOpenFDs());
    assertEquals(0, fds.getClosableFDs());
    PooledFileRab a = construct(sz);
    PooledFileRab b = construct(sz);
    assertEquals(1, fds.getOpenFDs());
    assertEquals(1, fds.getClosableFDs());
    assertFalse(a.isLocked());
    assertFalse(b.isLocked());
    Rab.RabLock lock = a.lockOpen();
    try {
      assertTrue(a.isLocked());
      assertFalse(b.isLocked());
      assertEquals(1, fds.getOpenFDs());
      assertEquals(0, fds.getClosableFDs());
    } finally {
      lock.unlock();
      assertFalse(a.isLocked());
      assertEquals(1, fds.getOpenFDs());
      assertEquals(1, fds.getClosableFDs());
    }
    a.close();
    b.close();
    assertEquals(0, fds.getOpenFDs());
    assertEquals(0, fds.getClosableFDs());
    a.dispose();
    b.dispose();
  }

  /** Thanks bertm */
  @Test
  void testLocksB() throws IOException {
    fds.setMaxFDs(1);
    PooledFileRab a = construct(0);
    PooledFileRab b = construct(0);
    Rab.RabLock lock = b.lockOpen();
    lock.unlock();
    a.close();
    b.close();
    a.dispose();
    b.dispose();
    assertEquals(0, fds.getOpenFDs());
    assertEquals(0, fds.getClosableFDs());
  }

  @Test
  void testLockedNotClosable() throws IOException {
    int sz = 1024;
    fds.setMaxFDs(2);
    PooledFileRab a = construct(sz);
    PooledFileRab b = construct(sz);
    assertEquals(2, fds.getOpenFDs());
    assertEquals(2, fds.getClosableFDs());
    assertTrue(a.isOpen());
    assertTrue(b.isOpen());
    assertFalse(a.isLocked());
    assertFalse(b.isLocked());
    // Open and open FD -> locked
    Rab.RabLock la = a.lockOpen();
    assertEquals(2, fds.getOpenFDs());
    assertEquals(1, fds.getClosableFDs());
    Rab.RabLock lb = b.lockOpen();
    assertEquals(2, fds.getOpenFDs());
    assertEquals(0, fds.getClosableFDs());
    la.unlock();
    lb.unlock();
    assertEquals(2, fds.getOpenFDs());
    assertEquals(2, fds.getClosableFDs());
    a.close();
    b.close();
  }

  @Test
  void testLockedNotClosableFromNotOpenFD() throws IOException {
    int sz = 1024;
    fds.setMaxFDs(2);
    PooledFileRab a = construct(sz);
    PooledFileRab b = construct(sz);
    assertEquals(2, fds.getOpenFDs());
    assertEquals(2, fds.getClosableFDs());
    assertTrue(a.isOpen());
    assertTrue(b.isOpen());
    // Close the RAFs to exercise the other code path.
    a.closeChannel();
    b.closeChannel();
    assertFalse(a.isLocked());
    assertFalse(b.isLocked());
    // Open and open FD -> locked
    Rab.RabLock la = a.lockOpen();
    assertEquals(1, fds.getOpenFDs());
    assertEquals(1, fds.getClosableFDs());
    Rab.RabLock lb = b.lockOpen();
    assertEquals(2, fds.getOpenFDs());
    assertEquals(0, fds.getClosableFDs());
    la.unlock();
    lb.unlock();
    assertEquals(2, fds.getOpenFDs());
    assertEquals(2, fds.getClosableFDs());
    a.close();
    b.close();
  }

  /**
   * Test that locking enforces limits and blocks when appropriate.
   *
   * @throws InterruptedException
   */
  @Test
  void testLockBlocking() throws IOException, InterruptedException {
    int sz = 1024;
    fds.setMaxFDs(1);
    assertEquals(0, fds.getOpenFDs());
    final PooledFileRab a = construct(sz);
    final PooledFileRab b = construct(sz);
    assertEquals(1, fds.getOpenFDs());
    assertFalse(a.isLocked());
    assertFalse(b.isLocked());
    Rab.RabLock lock = a.lockOpen();
    assertTrue(a.isOpen());
    assertEquals(1, fds.getOpenFDs());
    // Now try to lock on a second thread.
    // It should wait until the first thread unlocks.
    class Status {
      boolean hasStarted;
      boolean hasLocked;
      boolean canFinish;
      boolean hasFinished;
      boolean success;
    }
    final Status s = new Status();
    Runnable r =
        () -> {
          synchronized (s) {
            s.hasStarted = true;
            s.notify();
          }
          try {
            Rab.RabLock lock1 = b.lockOpen();
            synchronized (s) {
              s.hasLocked = true;
              s.notify();
            }
            synchronized (s) {
              while (!s.canFinish)
                try {
                  s.wait();
                } catch (InterruptedException e) {
                  // Ignore.
                }
            }
            lock1.unlock();
            synchronized (s) {
              s.success = true;
            }
          } catch (IOException e) {
            e.printStackTrace();
            fail("Caught IOException trying to lock: " + e);
          } finally {
            synchronized (s) {
              s.hasFinished = true;
              s.notify();
            }
          }
        };
    new Thread(r).start();
    // Wait for it to start.
    synchronized (s) {
      while (!s.hasStarted) {
        s.wait();
      }
      assertFalse(s.hasLocked);
      assertFalse(s.hasFinished);
    }
    assertEquals(1, fds.getOpenFDs());
    assertTrue(a.isOpen());
    assertFalse(b.isOpen());
    // Wait while holding lock, to give it some time to progress if it's buggy.
    Thread.sleep(100);
    synchronized (s) {
      assertFalse(s.hasLocked);
      assertFalse(s.hasFinished);
    }
    assertEquals(1, fds.getOpenFDs());
    assertTrue(a.isOpen());
    assertFalse(b.isOpen());
    // Now release lock.
    lock.unlock();
    // Wait for it to proceed.
    synchronized (s) {
      while (!(s.hasLocked || s.hasFinished)) s.wait();
      assertTrue(s.hasLocked);
    }
    assertFalse(a.isOpen());
    assertTrue(b.isOpen());
    assertTrue(b.isLocked());
    assertEquals(1, fds.getOpenFDs());

    // Now let it proceed.
    synchronized (s) {
      s.canFinish = true;
      s.notifyAll();
      while (!s.hasFinished) {
        s.wait();
      }
      assertTrue(s.success);
    }
    assertFalse(a.isLocked());
    assertFalse(b.isLocked());
    assertEquals(1, fds.getClosableFDs());
    assertEquals(1, fds.getOpenFDs());
    a.close();
    assertEquals(1, fds.getOpenFDs());
    b.close();
    assertEquals(0, fds.getOpenFDs());
    a.dispose();
    b.dispose();
  }

  @BeforeEach
  void setUp() throws IOException {
    Files.createDirectories(base);
  }

  @AfterEach
  void tearDown() {
    FileSystem.removeAll(base);
  }

  /** Simplest test for pooling. TODO Add more. */
  @Test
  void testSimplePooling() throws IOException {
    for (int sz : TEST_LIST) innerTestSimplePooling(sz);
  }

  @Override
  protected PooledFileRab construct(long size) throws IOException {
    Path f = Files.createTempFile(base, "test", ".tmp");
    return new PooledFileRab(f, false, size, -1, true, fds);
  }

  private void innerTestSimplePooling(int sz) throws IOException {
    fds.setMaxFDs(1);
    PooledFileRab a = construct(sz);
    PooledFileRab b = construct(sz);
    byte[] buf1 = new byte[sz];
    byte[] buf2 = new byte[sz];
    Random r = new Random(1153);
    r.nextBytes(buf1);
    r.nextBytes(buf2);
    a.pwrite(0, buf1, 0, buf1.length);
    b.pwrite(0, buf2, 0, buf2.length);
    byte[] cmp1 = new byte[sz];
    byte[] cmp2 = new byte[sz];
    a.pread(0, cmp1, 0, cmp1.length);
    b.pread(0, cmp2, 0, cmp2.length);
    assertArrayEquals(cmp1, buf1);
    assertArrayEquals(cmp2, buf2);
    a.close();
    b.close();
    a.dispose();
    b.dispose();
  }

  private final Path base = Path.of("tmp.pooled-random-access-file-wrapper-test");
  private final PooledFileRab.FdTracker fds = new PooledFileRab.FdTracker(100);

  // FIXME more tests???

}
