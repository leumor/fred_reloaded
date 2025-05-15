package hyphanet.support.compress;

import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketFactory;
import hyphanet.support.io.storage.bucket.RandomAccessBucket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompressor extends AbstractCompressor {

  @Override
  public Bucket compress(Bucket data, BucketFactory bf, long maxReadLength, long maxWriteLength)
      throws IOException {
    RandomAccessBucket output = bf.makeBucket(maxWriteLength);
    try (var is = data.getInputStream();
        var os = output.getOutputStream()) {

      // force OS byte to 0 regardless of Java version (java 16 changed to setting 255 which would
      // break hashes)
      SingleOffsetReplacingOutputStream osByteFixingOs =
          new SingleOffsetReplacingOutputStream(os, 9, 0);
      compress(is, osByteFixingOs, maxReadLength, maxWriteLength);
    }
    return output;
  }

  @Override
  protected OutputStream createCompressorOutputStream(OutputStream underlyingOutputStream)
      throws IOException {
    return new GZIPOutputStream(underlyingOutputStream);
  }

  @Override
  protected InputStream createDecompressorInputStream(InputStream underlyingInputStream)
      throws IOException {
    return new GZIPInputStream(underlyingInputStream);
  }

  @Override
  protected void finalizeCompression(OutputStream compressorOs) throws IOException {
    ((GZIPOutputStream) compressorOs).finish();
  }
}
