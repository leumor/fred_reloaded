package hyphanet.support.compress;

import hyphanet.support.io.stream.CountedOutputStream;
import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractCompressor implements Compressor {
  private static final Logger logger = LoggerFactory.getLogger(AbstractCompressor.class);

  @Override
  public long compress(
      InputStream is,
      OutputStream os,
      long maxReadLength,
      long maxWriteLength,
      long amountOfDataToCheckCompressionRatio,
      int minimumCompressionPercentage)
      throws IOException, CompressionRatioException {

    if (maxReadLength <= 0) throw new IllegalArgumentException(); // Or < 0 depending on exact req.

    try (CountedOutputStream cos = new CountedOutputStream(os);
        OutputStream compressorOs = createCompressorOutputStream(cos)) { // HOOK

      // Potentially another hook here for pre-processing 'os' or 'compressorOs'
      // e.g., for GzipCompressor's SingleOffsetReplacingOutputStream
      // or Bzip2's HeaderStreams.dimOutput. This could be part of createCompressorOutputStream.

      long read = 0;
      int bufferSize = 32768; // Could be configurable or a constant
      byte[] buffer = new byte[bufferSize];
      long iterationToCheckCompressionRatio = amountOfDataToCheckCompressionRatio / bufferSize;
      int i = 0;

      while (true) {
        int l = (int) Math.min(buffer.length, maxReadLength - read);
        int x = (l == 0 ? -1 : is.read(buffer, 0, l));

        if (x == -1) break;
        if (x == 0) throw new IOException("Returned zero from read()");

        compressorOs.write(buffer, 0, x);
        read += x;

        if (cos.written() > maxWriteLength) throw new CompressionOutputSizeException(cos.written());

        if (minimumCompressionPercentage != 0
            && amountOfDataToCheckCompressionRatio > 0
            && ++i == iterationToCheckCompressionRatio) {
          checkCompressionEffect(read, cos.written(), minimumCompressionPercentage);
        }
      }

      // Hook for finalization, e.g. GZIPOutputStream.finish()
      finalizeCompression(compressorOs); // HOOK

      if (cos.written() > maxWriteLength) throw new CompressionOutputSizeException(cos.written());
      return cos.written();
    }
  }

  @Override
  public long compress(
      InputStream input, OutputStream output, long maxReadLength, long maxWriteLength)
      throws IOException {
    try {
      return compress(input, output, maxReadLength, maxWriteLength, Long.MAX_VALUE, 0);
    } catch (CompressionRatioException e) {
      // Should not happen according to the contract of method
      //   {@link Compressor#compress(InputStream, OutputStream, long, long, long, int)}
      throw new IllegalStateException(e);
    }
  }

  @Override
  public long decompress(InputStream is, OutputStream os, long maxLength, long maxCheckSizeBytes)
      throws IOException {
    try (InputStream decompressorIs = createDecompressorInputStream(is)) { // HOOK
      long written = 0;
      int bufSize = 32768;
      if (maxLength > 0 && maxLength < bufSize) bufSize = (int) maxLength;
      byte[] buffer = new byte[bufSize];

      while (true) {
        int expectedBytesRead = (int) Math.min(buffer.length, maxLength - written);

        int bytesRead = decompressorIs.read(buffer, 0, buffer.length);

        // Check if we read more than allowed if maxLength is enforced strictly before write
        if (expectedBytesRead < bytesRead) {
          logger.info(
              "expectedBytesRead={}, bytesRead={}, written={}, maxLength={} throwing a CompressionOutputSizeException",
              expectedBytesRead,
              bytesRead,
              written,
              maxLength);
          if (maxCheckSizeBytes > 0) {
            written += bytesRead;
            while (true) {
              expectedBytesRead =
                  (int) Math.min(buffer.length, maxLength + maxCheckSizeBytes - written);
              bytesRead = decompressorIs.read(buffer, 0, expectedBytesRead);
              if (bytesRead <= -1) throw new CompressionOutputSizeException(written);
              if (bytesRead == 0) throw new IOException("Returned zero from read()");
              written += bytesRead;
            }
          }
          throw new CompressionOutputSizeException();
        }

        if (bytesRead == -1) {
          finalizeDecompression(os); // HOOK
          return written;
        }
        if (bytesRead == 0) throw new IOException("Returned zero from read()");

        os.write(buffer, 0, bytesRead);
        written += bytesRead;
      }
    }
  }

  @Override
  public int decompress(byte[] dbuf, int i, int j, byte[] output) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(dbuf, i, j);
    ByteArrayOutputStream baos =
        new ByteArrayOutputStream(output.length); // Or a suitable initial size
    int bytes;
    // The stream-based decompress is the one concrete classes *must* implement
    decompress(bais, baos, output.length, -1); // Or pass appropriate maxCheckSizeBytes
    bytes = baos.size();

    if (bytes > output.length) {
      throw new CompressionOutputSizeException(bytes);
    }

    byte[] buf = baos.toByteArray();
    System.arraycopy(buf, 0, output, 0, bytes);
    return bytes;
  }

  void checkCompressionEffect(
      long rawDataVolume, long compressedDataVolume, int minimumCompressionPercentage)
      throws CompressionRatioException {
    assert rawDataVolume != 0;
    assert minimumCompressionPercentage != 0;

    long compressionPercentage = 100 - compressedDataVolume * 100 / rawDataVolume;
    if (compressionPercentage < minimumCompressionPercentage) {
      throw new CompressionRatioException(
          "Compression has no effect. Compression percentage: " + compressionPercentage);
    }
  }

  // Default empty implementation, GzipCompressor would override
  protected void finalizeCompression(OutputStream compressorOs) throws IOException {}

  // Abstract hook methods to be implemented by subclasses
  protected abstract OutputStream createCompressorOutputStream(OutputStream underlyingOutputStream)
      throws IOException;

  protected abstract InputStream createDecompressorInputStream(InputStream underlyingInputStream)
      throws IOException;

  // Optional hook for finalizing decompression (e.g., flushing output stream)
  protected void finalizeDecompression(OutputStream os) throws IOException {
    os.flush(); // Common case
  }
}
