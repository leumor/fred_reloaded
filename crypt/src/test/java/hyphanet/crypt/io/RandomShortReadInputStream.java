package hyphanet.crypt.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/** Takes an OutputStream and randomly truncates its reads */
class RandomShortReadInputStream extends FilterInputStream {

  RandomShortReadInputStream(InputStream is, Random random) {
    super(is);
    this.random = random;
  }

  @Override
  public int read(byte[] buf) throws IOException {
    return read(buf, 0, buf.length);
  }

  @Override
  public int read(byte[] buf, int offset, int length) throws IOException {
    if (length > 3 && random.nextBoolean()) {
      if (length > 16 && random.nextBoolean()) {
        length = random.nextInt(16);
      } else {
        length = random.nextInt(length);
      }
    }
    return in.read(buf, offset, length);
  }

  private final Random random;
}
