/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io;

import hyphanet.support.HexUtil;
import org.jspecify.annotations.Nullable;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A specialized {@link FilterInputStream} implementation that provides line reading capabilities with
 * support for both UTF-8 and ISO-8859-1 encodings.
 * <p>
 * This class handles both Unix (\n) and Windows (\r\n) line endings automatically. It provides
 * mechanisms to limit the maximum line length to prevent out-of-memory conditions when processing
 * untrusted input.
 * </p>
 */
public class LineReadingInputStream extends FilterInputStream implements LineReader {

    public LineReadingInputStream(InputStream in) {
        super(in);
    }

    /**
     * Read a \n or \r\n terminated line of UTF-8 or ISO-8859-1.
     *
     * @param maxLength  The maximum length of a line. If a line is longer than this, we throw
     *                   IOException rather than keeping on reading it forever.
     * @param bufferSize The initial size of the read buffer.
     * @param utf        If true, read as UTF-8, if false, read as ISO-8859-1.
     */
    @Override
    public @Nullable String readLine(int maxLength, int bufferSize, boolean utf) throws IOException {
        if (maxLength < 1) {
            return null;
        }

        if (maxLength <= bufferSize) {
            bufferSize = maxLength + 1; // Buffer too big, shrink it (add 1 for the optional \r)
        }

        return markSupported() ? readLineWithMarking(maxLength, bufferSize, utf) :
            readLineWithoutMarking(maxLength, bufferSize, utf);
    }

    protected @Nullable String readLineWithoutMarking(int maxLength, int bufferSize, boolean utf)
        throws IOException {
        byte[] buf = new byte[calculateBufferSize(maxLength, bufferSize)];
        int ctr = 0;

        while (true) {
            int x = read();
            if (x == -1) {
                return handleEndOfStream(buf, ctr, -1, utf);
            }

            if (x == '\n') {
                return createLineString(buf, ctr, utf);
            }

            if (ctr >= maxLength) {
                throw createTooLongException(buf, ctr, maxLength, utf);
            }

            if (ctr >= buf.length) {
                buf = Arrays.copyOf(buf, Math.min(buf.length * 2, maxLength));
            }

            buf[ctr++] = (byte) x;
        }
    }

    /**
     * Reads a line when mark/reset is supported by the underlying stream.
     *
     * @param maxLength  maximum line length
     * @param bufferSize initial buffer size
     * @param utf        encoding flag
     *
     * @return the read line
     *
     * @throws IOException if an I/O error occurs
     */
    private @Nullable String readLineWithMarking(int maxLength, int bufferSize, boolean utf)
        throws IOException {
        byte[] buf = new byte[calculateBufferSize(maxLength, bufferSize)];
        int ctr = 0;

        mark(maxLength + 2); // Account for possible \r\n

        while (true) {
            int bytesRead = read(buf, ctr, buf.length - ctr);

            if (bytesRead <= 0) {
                return handleEndOfStream(buf, ctr, bytesRead, utf);
            }

            // REDFLAG this is definitely safe with UTF_8 and ISO_8859_1, it may not be safe with some
            // wierd ones.
            int end = ctr + bytesRead;
            for (; ctr < end; ctr++) {
                if (buf[ctr] == '\n') {
                    String line = createLineString(buf, ctr, utf);
                    reset();
                    //noinspection ResultOfMethodCallIgnored
                    skip(ctr + 1);
                    return line;
                }
            }

            if (ctr >= maxLength) {
                throw createTooLongException(buf, ctr, maxLength, utf);
            }

            if ((buf.length < maxLength) && (buf.length - ctr < bufferSize)) {
                buf = Arrays.copyOf(buf, Math.min(buf.length * 2, maxLength));
            }

        }
    }

    // Helper methods
    private static int calculateBufferSize(int maxLength, int requestedSize) {
        return Math.max(Math.min(MIN_BUFFER_SIZE, maxLength),
                        Math.min(DEFAULT_BUFFER_SIZE, requestedSize));
    }

    private @Nullable String handleEndOfStream(byte[] buf, int ctr, int bytesRead, boolean utf)
        throws EOFException {
        if (ctr == 0 && bytesRead < 0) {
            return null;
        }
        if (bytesRead == 0) {
            // Don't busy-loop. Probably a socket closed or something.
            // If not, it's not a salvageable situation; either way throw.
            throw new EOFException("Unexpected end of stream");
        }
        return createLineString(buf, ctr, utf);
    }

    /**
     * Creates a line string from the buffer, handling CR/LF.
     *
     * @param buf    the buffer containing the line
     * @param endPos the position of the line end
     * @param utf    encoding flag
     *
     * @return the line string
     */
    private String createLineString(byte[] buf, int endPos, boolean utf) {
        if (endPos == 0) {
            return "";
        }
        boolean hasCR = (endPos > 0 && buf[endPos - 1] == '\r');
        return new String(buf, 0, hasCR ? endPos - 1 : endPos,
                          utf ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
    }

    private static TooLongException createTooLongException(
        byte[] buf, int ctr, int maxLength, boolean utf) {
        return new TooLongException(
            String.format("Line exceeded maximum length of %d bytes%n%s%n%s", maxLength,
                          HexUtil.bytesToHex(buf, 0, ctr), new String(buf, 0, ctr,
                                                                      utf ? StandardCharsets.UTF_8 :
                                                                          StandardCharsets.ISO_8859_1)));
    }

    /**
     * Minimum buffer size for reading operations
     */
    private static final int MIN_BUFFER_SIZE = 128;

    /**
     * Default buffer size for reading operations
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024;


}
