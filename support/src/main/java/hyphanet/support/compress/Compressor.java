/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.compress;

import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A data compressor. Contains methods to get all data compressors. This is for single-file
 * compression (gzip, bzip2) as opposed to archives.
 */
public interface Compressor {

  /**
   * Compress the data.
   *
   * @param data The bucket to read from.
   * @param bf The means to create a new bucket.
   * @param maxReadLength The maximum number of bytes to read from the input bucket.
   * @param maxWriteLength The maximum number of bytes to write to the output bucket. If this is
   *     exceeded, throw a CompressionOutputSizeException.
   * @return The compressed data.
   * @throws IOException If an error occurs reading or writing data.
   * @throws CompressionOutputSizeException If the compressed data is larger than maxWriteLength.
   */
  Bucket compress(Bucket data, BucketFactory bf, long maxReadLength, long maxWriteLength)
      throws IOException, CompressionOutputSizeException;

  /**
   * Compress the data.
   *
   * @param input The InputStream to read from.
   * @param output The OutputStream to write to.
   * @param maxReadLength The maximum number of bytes to read from the input bucket.
   * @param maxWriteLength The maximum number of bytes to write to the output bucket. If this is
   *     exceeded, throw a CompressionOutputSizeException.
   * @return The compressed data.
   * @throws IOException If an error occurs reading or writing data.
   * @throws CompressionOutputSizeException If the compressed data is larger than maxWriteLength.
   */
  long compress(InputStream input, OutputStream output, long maxReadLength, long maxWriteLength)
      throws IOException, CompressionOutputSizeException;

  /**
   * Compress the data (@see {@link #compress(InputStream, OutputStream, long, long)}) with checking
   * of compression effect.
   *
   * @param amountOfDataToCheckCompressionRatio The data amount after compression of which we will
   *     check whether we have got the desired effect.
   * @param minimumCompressionPercentage The minimal desired compression effect, %. A value of 0
   *     means that the compression effect will not be checked.
   * @throws CompressionRatioException If the desired compression effect is not achieved.
   */
  long compress(
      InputStream input,
      OutputStream output,
      long maxReadLength,
      long maxWriteLength,
      long amountOfDataToCheckCompressionRatio,
      int minimumCompressionPercentage)
      throws IOException, CompressionRatioException;

  /**
   * Decompress data.
   *
   * @param input Where to read the data to decompress from
   * @param output Where to write the final product to
   * @param maxLength The maximum length to decompress (we throw if more is present).
   * @param maxEstimateSizeLength If the data is too big, and this is >0, read up to this many bytes
   *     in order to try to get the data size.
   * @return Number of bytes copied
   * @throws IOException
   * @throws CompressionOutputSizeException
   */
  long decompress(
      InputStream input, OutputStream output, long maxLength, long maxEstimateSizeLength)
      throws IOException;

  /**
   * Decompress in RAM only.
   *
   * @param dbuf Input buffer.
   * @param i Offset to start reading from.
   * @param j Number of bytes to read.
   * @param output Output buffer.
   * @throws CompressionOutputSizeException
   * @returns The number of bytes actually written.
   */
  int decompress(byte[] dbuf, int i, int j, byte[] output) throws IOException;
}
