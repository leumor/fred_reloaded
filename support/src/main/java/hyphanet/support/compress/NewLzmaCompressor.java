/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.compress;

import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;

/** * {@link Compressor} for BZip2 streams */
public class NewLzmaCompressor extends AbstractCompressor {

  @Override
  public Bucket compress(Bucket data, BucketFactory bf, long maxReadLength, long maxWriteLength)
      throws IOException {
    Bucket output = bf.makeBucket(maxWriteLength);
    try (InputStream is = data.getInputStream();
        OutputStream os = output.getOutputStream()) {
      compress(is, os, maxReadLength, maxWriteLength);
    }
    return output;
  }

  @Override
  protected OutputStream createCompressorOutputStream(OutputStream underlyingOutputStream)
      throws IOException {
    return new LZMACompressorOutputStream(underlyingOutputStream);
  }

  @Override
  protected InputStream createDecompressorInputStream(InputStream underlyingInputStream)
      throws IOException {
    return new LZMACompressorInputStream(underlyingInputStream);
  }
}
