/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package hyphanet.support.io.stream;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A wrapper that shields against exceptions thrown by {@link InputStream#skip(long)} and delegates
 * to {@link InputStream#read(byte[], int, int)} instead.
 *
 * <p>Some implementations of {@link InputStream#skip(long)} might throw an {@link IOException} if
 * the stream is not seekable. A notable example is {@link System#in}. For such streams, invoking
 * {@code skip} directly is not possible. This class provides a workaround by overriding {@link
 * #skip(long)} and internally using {@link #read(byte[], int, int)} to discard bytes, effectively
 * simulating the skip operation.
 *
 * <p>While skipping via reading is generally less efficient than native skip implementations, it
 * ensures compatibility with non-seekable input streams and prevents unexpected exceptions when
 * skipping is attempted.
 *
 * @since 1.17
 */
@SuppressWarnings("java:S4929")
public class SkipShieldingInputStream extends FilterInputStream {
  /**
   * The size of the buffer used for skipping bytes when the underlying stream's {@code skip()}
   * method is problematic. This buffer size is chosen to be reasonably large to improve efficiency,
   * but small enough to be memory-friendly.
   */
  private static final int SKIP_BUFFER_SIZE = 8192;

  /**
   * A shared static buffer used to discard bytes when simulating skip operations. This buffer is
   * shared across all instances of {@link SkipShieldingInputStream} as its content is discarded and
   * not relevant to the stream's data. Using a static buffer reduces memory allocation overhead.
   */
  private static final byte[] SKIP_BUFFER = new byte[SKIP_BUFFER_SIZE];

  /**
   * Constructs a {@code SkipShieldingInputStream} that wraps the given input stream.
   *
   * @param in the underlying input stream to be shielded.
   * @see FilterInputStream#FilterInputStream(InputStream)
   */
  public SkipShieldingInputStream(InputStream in) {
    super(in);
  }

  /**
   * Skips over and discards {@code n} bytes of data from the input stream.
   *
   * <p>This implementation overrides the default {@link FilterInputStream#skip(long)} method.
   * Instead of directly delegating to the underlying stream's {@code skip} method, which might
   * throw an {@link IOException} for non-seekable streams, this method simulates skipping by
   * reading and discarding bytes from the stream.
   *
   * <p>The skipping is performed by repeatedly calling {@link #read(byte[], int, int)} with a
   * temporary buffer ({@link #SKIP_BUFFER}) until the requested number of bytes has been skipped or
   * the end of the stream is reached.
   *
   * @throws IOException if an I/O error occurs. Specifically, an {@code IOException} may be thrown
   *     if the underlying input stream throws an {@code IOException} during the {@code read}
   *     operations used for skipping.
   */
  @Override
  public long skip(long n) throws IOException {
    if (n <= 0) {
      return 0L;
    }

    long skipped = 0L;
    int readCount;
    int currentSkip;
    while (skipped < n) {
      // Make sure skipped length is less than the size of SKIP_BUFFER
      currentSkip = (int) Math.min(n - skipped, SKIP_BUFFER_SIZE);

      readCount = read(SKIP_BUFFER, 0, currentSkip);
      if (readCount < 0) {
        break; // End of stream
      }
      skipped += readCount;
    }
    return skipped;
  }
}
