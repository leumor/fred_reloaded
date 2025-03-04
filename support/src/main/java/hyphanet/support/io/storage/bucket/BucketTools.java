package hyphanet.support.io.storage.bucket;

import hyphanet.crypt.hash.Sha256;
import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.wrapper.*;
import hyphanet.support.io.storage.rab.*;
import hyphanet.support.io.util.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGeneratorFactory;

/** Helper functions for working with Buckets. */
public class BucketTools {

  static final ArrayBucketFactory ARRAY_FACTORY = new ArrayBucketFactory();
  private static final int BUFFER_SIZE = 64 * 1024;
  private static final Logger logger = LoggerFactory.getLogger(BucketTools.class);

  /**
   * Copy from the input stream of <code>src</code> to the output stream of <code>dest</code>.
   *
   * @param src
   * @param dst
   * @throws IOException
   */
  public static void copy(Bucket src, Bucket dst) throws IOException {
    OutputStream out = dst.getOutputStreamUnbuffered();
    InputStream in = src.getInputStreamUnbuffered();
    ReadableByteChannel readChannel = Channels.newChannel(in);
    WritableByteChannel writeChannel = Channels.newChannel(out);
    try {

      // No benefit to allocateDirect() as we're wrapping streams anyway, and worse,
      // it'd be a memory leak.
      ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
      while (readChannel.read(buffer) != -1) {
        buffer.flip();
        while (buffer.hasRemaining()) {
          writeChannel.write(buffer);
        }
        buffer.clear();
      }

    } finally {
      writeChannel.close();
      readChannel.close();
    }
  }

  public static void zeroPad(Bucket b, long size) throws IOException {
    OutputStream out = b.getOutputStreamUnbuffered();

    try {
      // Initialized to zero by default.
      byte[] buffer = new byte[16384];

      long count = 0;
      while (count < size) {
        long nRequired = buffer.length;
        if (nRequired > size - count) {
          nRequired = size - count;
        }
        out.write(buffer, 0, (int) nRequired);
        count += nRequired;
      }

    } finally {
      out.close();
    }
  }

  public static void paddedCopy(Bucket from, Bucket to, long nBytes, int blockSize)
      throws IOException {

    if (nBytes > blockSize) {
      throw new IllegalArgumentException("nBytes > blockSize");
    }

    OutputStream out = null;
    InputStream in = null;

    try {

      out = to.getOutputStreamUnbuffered();
      byte[] buffer = new byte[16384];
      in = from.getInputStreamUnbuffered();

      long count = 0;
      while (count != nBytes) {
        long nRequired = nBytes - count;
        if (nRequired > buffer.length) {
          nRequired = buffer.length;
        }
        long nRead = in.read(buffer, 0, (int) nRequired);
        if (nRead == -1) {
          throw new IOException("Not enough data in source bucket.");
        }
        out.write(buffer, 0, (int) nRead);
        count += nRead;
      }

      if (count < blockSize) {
        // hmmm... better to just allocate a new buffer
        // instead of explicitly zeroing the old one?
        // Zero pad to blockSize
        long padLength = buffer.length;
        if (padLength > blockSize - nBytes) {
          padLength = blockSize - nBytes;
        }
        for (int i = 0; i < padLength; i++) {
          buffer[i] = 0;
        }

        while (count != blockSize) {
          long nRequired = blockSize - count;
          if (blockSize - count > buffer.length) {
            nRequired = buffer.length;
          }
          out.write(buffer, 0, (int) nRequired);
          count += nRequired;
        }
      }
    } finally {
      if (in != null) {
        in.close();
      }
      if (out != null) {
        out.close();
      }
    }
  }

  public static Bucket[] makeBuckets(BucketFactory bf, int count, int size) throws IOException {
    Bucket[] ret = new Bucket[count];
    for (int i = 0; i < count; i++) {
      ret[i] = bf.makeBucket(size);
    }
    return ret;
  }

  public static int[] nullIndices(Bucket[] array) {
    List<Integer> list = new ArrayList<Integer>();
    for (int i = 0; i < array.length; i++) {
      if (array[i] == null) {
        list.add(i);
      }
    }

    int[] ret = new int[list.size()];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = list.get(i);
    }
    return ret;
  }

  public static int[] nonNullIndices(Bucket[] array) {
    List<Integer> list = new ArrayList<Integer>();
    for (int i = 0; i < array.length; i++) {
      if (array[i] != null) {
        list.add(i);
      }
    }

    int[] ret = new int[list.size()];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = list.get(i);
    }
    return ret;
  }

  public static Bucket[] nonNullBuckets(Bucket[] array) {
    List<Bucket> list = new ArrayList<Bucket>(array.length);
    for (int i = 0; i < array.length; i++) {
      if (array[i] != null) {
        list.add(array[i]);
      }
    }

    Bucket[] ret = new Bucket[list.size()];
    return list.toArray(ret);
  }

  /**
   * Read the entire bucket in as a byte array. Not a good idea unless it is very small! Don't call
   * if concurrent writes may be happening.
   *
   * @throws IOException If there was an error reading from the bucket.
   * @throws OutOfMemoryError If it was not possible to allocate enough memory to contain the entire
   *     bucket.
   */
  public static byte[] toByteArray(Bucket bucket) throws IOException {
    long size = bucket.size();
    if (size > Integer.MAX_VALUE) {
      throw new OutOfMemoryError();
    }
    byte[] data = new byte[(int) size];
    InputStream is = bucket.getInputStreamUnbuffered();
    try (DataInputStream dis = new DataInputStream(is)) {
      dis.readFully(data);
    }
    return data;
  }

  public static int toByteArray(Bucket bucket, byte[] output) throws IOException {
    long size = bucket.size();
    if (size > output.length) {
      throw new IllegalArgumentException("Data does not fit in provided buffer");
    }
    InputStream is = null;
    try {
      is = bucket.getInputStreamUnbuffered();
      int moved = 0;
      while (true) {
        if (moved == size) {
          return moved;
        }
        int x = is.read(output, moved, (int) (size - moved));
        if (x == -1) {
          return moved;
        }
        moved += x;
      }
    } finally {
      if (is != null) {
        is.close();
      }
    }
  }

  public static RandomAccessBucket makeImmutableBucket(BucketFactory bucketFactory, byte[] data)
      throws IOException {
    return makeImmutableBucket(bucketFactory, data, data.length);
  }

  public static RandomAccessBucket makeImmutableBucket(
      BucketFactory bucketFactory, byte[] data, int length) throws IOException {
    return makeImmutableBucket(bucketFactory, data, 0, length);
  }

  public static RandomAccessBucket makeImmutableBucket(
      BucketFactory bucketFactory, byte[] data, int offset, int length) throws IOException {
    RandomAccessBucket bucket = bucketFactory.makeBucket(length);
    OutputStream os = bucket.getOutputStreamUnbuffered();
    try {
      os.write(data, offset, length);
    } finally {
      os.close();
    }
    bucket.setReadOnly();
    return bucket;
  }

  public static byte[] hash(Bucket data) throws IOException {
    InputStream is = data.getInputStreamUnbuffered();
    try {
      MessageDigest md = Sha256.getMessageDigest();
      try {
        long bucketLength = data.size();
        long bytesRead = 0;
        byte[] buf = new byte[BUFFER_SIZE];
        while ((bytesRead < bucketLength) || (bucketLength == -1)) {
          int readBytes = is.read(buf);
          if (readBytes < 0) {
            break;
          }
          bytesRead += readBytes;
          if (readBytes > 0) {
            md.update(buf, 0, readBytes);
          }
        }
        if ((bytesRead < bucketLength) && (bucketLength > 0)) {
          throw new EOFException();
        }
        if ((bytesRead != bucketLength) && (bucketLength > 0)) {
          throw new IOException(
              "Read " + bytesRead + " but bucket length " + bucketLength + " on " + data + '!');
        }
        byte[] retval = md.digest();
        return retval;
      } finally {
        Sha256.returnMessageDigest(md);
      }
    } finally {
      if (is != null) {
        is.close();
      }
    }
  }

  /**
   * Copy the given quantity of data from the given bucket to the given OutputStream.
   *
   * @throws IOException If there was an error reading from the bucket or writing to the stream.
   */
  public static long copyTo(Bucket decodedData, OutputStream os, long truncateLength)
      throws IOException {
    if (truncateLength == 0) {
      return 0;
    }
    if (truncateLength < 0) {
      truncateLength = Long.MAX_VALUE;
    }
    InputStream is = decodedData.getInputStreamUnbuffered();
    try {
      int bufferSize = BUFFER_SIZE;
      if (truncateLength > 0 && truncateLength < bufferSize) {
        bufferSize = (int) truncateLength;
      }
      byte[] buf = new byte[bufferSize];
      long moved = 0;
      while (moved < truncateLength) {
        // DO NOT move the (int) inside the Math.min()! big numbers truncate to
        // negative numbers.
        int bytes = (int) Math.min(buf.length, truncateLength - moved);
        if (bytes <= 0) {
          throw new IllegalStateException(
              "bytes=" + bytes + ", truncateLength=" + truncateLength + ", moved=" + moved);
        }
        bytes = is.read(buf, 0, bytes);
        if (bytes <= 0) {
          if (truncateLength == Long.MAX_VALUE) {
            break;
          }
          IOException ioException =
              new IOException(
                  "Could not move required quantity of data in copyTo: "
                      + bytes
                      + " (moved "
                      + moved
                      + " of "
                      + truncateLength
                      + "): unable to read from "
                      + is);
          ioException.printStackTrace();
          throw ioException;
        }
        os.write(buf, 0, bytes);
        moved += bytes;
      }
      return moved;
    } finally {
      is.close();
      os.flush();
    }
  }

  /** Copy data from an InputStream into a Bucket. */
  public static void copyFrom(Bucket bucket, InputStream is, long truncateLength)
      throws IOException {
    OutputStream os = bucket.getOutputStreamUnbuffered();
    byte[] buf = new byte[BUFFER_SIZE];
    if (truncateLength < 0) {
      truncateLength = Long.MAX_VALUE;
    }
    try {
      long moved = 0;
      while (moved < truncateLength) {
        // DO NOT move the (int) inside the Math.min()! big numbers truncate to
        // negative numbers.
        int bytes = (int) Math.min(buf.length, truncateLength - moved);
        if (bytes <= 0) {
          throw new IllegalStateException(
              "bytes=" + bytes + ", truncateLength=" + truncateLength + ", moved=" + moved);
        }
        bytes = is.read(buf, 0, bytes);
        if (bytes <= 0) {
          if (truncateLength == Long.MAX_VALUE) {
            break;
          }
          IOException ioException =
              new IOException(
                  "Could not move required quantity of data in copyFrom: "
                      + bytes
                      + " (moved "
                      + moved
                      + " of "
                      + truncateLength
                      + "): unable to read from "
                      + is);
          ioException.printStackTrace();
          throw ioException;
        }
        os.write(buf, 0, bytes);
        moved += bytes;
      }
    } finally {
      os.close();
    }
  }

  /**
   * Split the data into a series of read-only Bucket's.
   *
   * @param origData The original data Bucket.
   * @param splitSize The number of bytes to put into each bucket.
   *     <p>If the passed-in Bucket is a FileBucket, will be efficiently split into
   *     ReadOnlyFileSliceBuckets, otherwise new buckets are created and the data written to them.
   *     <p>Note that this method will allocate a buffer of size splitSize.
   * @param bf
   * @param freeData
   * @param persistent If true, the data is persistent. This method is responsible for ensuring that
   *     the returned buckets HAVE ALREADY BEEN STORED TO THE DATABASE, using the provided handle.
   *     The point? SegmentedBCB's buckets have already been stored!!
   * @throws IOException If there is an error creating buckets, reading from the provided bucket, or
   *     writing to created buckets.
   */
  public static Bucket[] split(
      Bucket origData, int splitSize, BucketFactory bf, boolean freeData, boolean persistent)
      throws IOException {
    if (origData instanceof RegularFileBucket) {
      if (freeData) {
        logger.error(
            "Asked to free data when splitting a FileBucket ?!?!? Not "
                + "freeing as this would clobber the split result...");
      }
      Bucket[] buckets = ((RegularFileBucket) origData).split(splitSize);
      if (persistent) {
        return buckets;
      }
    }
    long length = origData.size();
    if (length > ((long) Integer.MAX_VALUE) * splitSize) {
      throw new IllegalArgumentException("Way too big!: " + length + " for " + splitSize);
    }
    int bucketCount = (int) (length / splitSize);
    if (length % splitSize > 0) {
      bucketCount++;
    }
    logger.info("Splitting bucket {} of size {} into {} buckets", origData, length, bucketCount);
    Bucket[] buckets = new Bucket[bucketCount];
    InputStream is = origData.getInputStreamUnbuffered();
    DataInputStream dis = null;
    try {
      dis = new DataInputStream(is);
      long remainingLength = length;
      byte[] buf = new byte[splitSize];
      for (int i = 0; i < bucketCount; i++) {
        int len = (int) Math.min(splitSize, remainingLength);
        Bucket bucket = bf.makeBucket(len);
        buckets[i] = bucket;
        dis.readFully(buf, 0, len);
        remainingLength -= len;
        OutputStream os = bucket.getOutputStreamUnbuffered();
        try {
          os.write(buf, 0, len);
        } finally {
          os.close();
        }
      }
    } finally {
      if (dis != null) {
        dis.close();
      } else {
        is.close();
      }
    }
    if (freeData) {
      origData.dispose();
    }
    return buckets;
  }

  /**
   * Pad a bucket with random data
   *
   * @param oldBucket
   * @param blockLength
   * @param bf
   * @param length
   * @return the padded bucket
   */
  public static Bucket pad(Bucket oldBucket, int blockLength, BucketFactory bf, int length)
      throws IOException {
    byte[] hash = BucketTools.hash(oldBucket);
    Bucket b = bf.makeBucket(blockLength);
    var rng = RandomGeneratorFactory.getDefault().create(hash);
    try (OutputStream os = b.getOutputStreamUnbuffered()) {
      BucketTools.copyTo(oldBucket, os, length);
      byte[] buf = new byte[BUFFER_SIZE];
      for (int x = length; x < blockLength; ) {
        int remaining = blockLength - x;
        int thisCycle = Math.min(remaining, buf.length);
        rng.nextBytes(buf); // FIXME??
        os.write(buf, 0, thisCycle);
        x += thisCycle;
      }
      os.close();
      if (b.size() != blockLength) {
        throw new IllegalStateException(
            "The bucket's size is " + b.size() + " whereas it should be " + blockLength + '!');
      }
      return b;
    }
  }

  public static byte[] pad(byte[] orig, int blockSize, int length) throws IOException {
    ArrayBucket b = new ArrayBucket(orig);
    Bucket ret = BucketTools.pad(b, blockSize, ARRAY_FACTORY, length);
    return BucketTools.toByteArray(ret);
  }

  public static boolean equalBuckets(Bucket a, Bucket b) throws IOException {
    if (a.size() != b.size()) {
      return false;
    }
    long size = a.size();
    InputStream aIn = null, bIn = null;
    try {
      aIn = a.getInputStreamUnbuffered();
      bIn = b.getInputStreamUnbuffered();
      return Stream.equalStreams(aIn, bIn, size);
    } finally {
      aIn.close();
      bIn.close();
    }
  }

  /** Fill a bucket with hard to identify random data */
  public static void fill(Bucket bucket, long length) throws IOException {
    OutputStream os = null;
    try {
      os = bucket.getOutputStreamUnbuffered();
      Stream.fill(os, length);
    } finally {
      if (os != null) {
        os.close();
      }
    }
  }

  /**
   * Copy the contents of a Bucket to a RandomAccessBuffer at a specific offset.
   *
   * @param bucket The bucket to read data from.
   * @param raf The RandomAccessBuffer to write to.
   * @param fileOffset The offset within raf to start writing at.
   * @param truncateLength The maximum number of bytes to transfer, or -1 to copy the whole bucket.
   * @return The number of bytes moved.
   * @throws IOException If something breaks while copying the data.
   */
  public static long copyTo(Bucket bucket, Rab raf, long fileOffset, long truncateLength)
      throws IOException {
    if (truncateLength == 0) {
      return 0;
    }
    if (truncateLength < 0) {
      truncateLength = Long.MAX_VALUE;
    }
    InputStream is = bucket.getInputStreamUnbuffered();
    try {
      int bufferSize = BUFFER_SIZE;
      if (truncateLength > 0 && truncateLength < bufferSize) {
        bufferSize = (int) truncateLength;
      }
      byte[] buf = new byte[bufferSize];
      long moved = 0;
      while (moved < truncateLength) {
        // DO NOT move the (int) inside the Math.min()! big numbers truncate to
        // negative numbers.
        int bytes = (int) Math.min(buf.length, truncateLength - moved);
        if (bytes <= 0) {
          throw new IllegalStateException(
              "bytes=" + bytes + ", truncateLength=" + truncateLength + ", moved=" + moved);
        }
        bytes = is.read(buf, 0, bytes);
        if (bytes <= 0) {
          if (truncateLength == Long.MAX_VALUE) {
            break;
          }
          IOException ioException =
              new IOException(
                  "Could not move required quantity of data in copyTo: "
                      + bytes
                      + " (moved "
                      + moved
                      + " of "
                      + truncateLength
                      + "): unable to read from "
                      + is);
          ioException.printStackTrace();
          throw ioException;
        }
        raf.pwrite(fileOffset, buf, 0, bytes);
        moved += bytes;
        fileOffset += bytes;
      }
      return moved;
    } finally {
      is.close();
    }
  }

  /**
   * Inverse of Bucket.storeTo(). Uses the magic value to identify the bucket type. FIXME Maybe we
   * should just pass the ClientContext?
   *
   * @throws IOException
   * @throws StorageFormatException
   * @throws ResumeFailedException
   */
  public static Bucket restoreFrom(
      DataInputStream dis,
      FilenameGenerator fg,
      PersistentFileTracker persistentFileTracker,
      MasterSecret masterKey)
      throws IOException, StorageFormatException, ResumeFailedException {
    int magic = dis.readInt();
    return switch (magic) {
      case AeadCryptBucket.MAGIC -> new AeadCryptBucket(dis, fg, persistentFileTracker, masterKey);
      case RegularFileBucket.MAGIC -> new RegularFileBucket(dis);
      case PersistentTempFileBucket.MAGIC -> new PersistentTempFileBucket(dis);
      case DelayedDisposeBucket.MAGIC ->
          new DelayedDisposeBucket(dis, fg, persistentFileTracker, masterKey);
      case DelayedDisposeRandomAccessBucket.MAGIC ->
          new DelayedDisposeRandomAccessBucket(dis, fg, persistentFileTracker, masterKey);
      case NoDisposeBucket.MAGIC -> new NoDisposeBucket(dis, fg, persistentFileTracker, masterKey);
      case PaddedEphemerallyEncryptedBucket.MAGIC ->
          new PaddedEphemerallyEncryptedBucket(dis, fg, persistentFileTracker, masterKey);
      case ReadOnlyFileSliceBucket.MAGIC -> new ReadOnlyFileSliceBucket(dis);
      case PaddedBucket.MAGIC -> new PaddedBucket(dis, fg, persistentFileTracker, masterKey);
      case PaddedRandomAccessBucket.MAGIC ->
          new PaddedRandomAccessBucket(dis, fg, persistentFileTracker, masterKey);
      case RabBucket.MAGIC -> new RabBucket(dis, fg, persistentFileTracker, masterKey);
      case EncryptedBucket.MAGIC -> new EncryptedBucket(dis, fg, persistentFileTracker, masterKey);
      default -> throw new StorageFormatException("Unknown magic value for bucket " + magic);
    };
  }

  /**
   * Restore a LockableRandomAccessBuffer from a DataInputStream. Inverse of storeTo(). FIXME Maybe
   * we should just pass the ClientContext?
   */
  public static Rab restoreRabFrom(
      DataInputStream dis,
      FilenameGenerator fg,
      PersistentFileTracker persistentFileTracker,
      MasterSecret masterSecret)
      throws IOException, StorageFormatException, ResumeFailedException {
    int magic = dis.readInt();
    return switch (magic) {
      case PooledFileRab.MAGIC -> new PooledFileRab(dis, fg, persistentFileTracker);
      case RegularFileRab.MAGIC -> new RegularFileRab(dis);
      case ReadOnlyRab.MAGIC -> new ReadOnlyRab(dis, fg, persistentFileTracker, masterSecret);
      case DelayedDisposeRab.MAGIC ->
          new DelayedDisposeRab(dis, fg, persistentFileTracker, masterSecret);
      case EncryptedRab.MAGIC -> EncryptedRab.create(dis, fg, persistentFileTracker, masterSecret);
      case PaddedRab.MAGIC -> new PaddedRab(dis, fg, persistentFileTracker, masterSecret);
      default -> throw new StorageFormatException("Unknown magic value for RAF " + magic);
    };
  }

  public static RandomAccessBucket toRandomAccessBucket(Bucket bucket, BucketFactory bf)
      throws IOException {
    if (bucket instanceof RandomAccessBucket) {
      return (RandomAccessBucket) bucket;
    }
    if (bucket instanceof DelayedDisposeBucket) {
      RandomAccessBucket ret = ((DelayedDisposeBucket) bucket).toRandomAccessBucket();
      if (ret != null) {
        return ret;
      }
    }
    RandomAccessBucket ret = bf.makeBucket(bucket.size());
    BucketTools.copy(bucket, ret);
    bucket.dispose();
    return ret;
  }
}
