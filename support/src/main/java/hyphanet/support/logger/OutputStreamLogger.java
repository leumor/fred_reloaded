package hyphanet.support.logger;

import hyphanet.support.logger.Logger.LogLevel;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * A specialized OutputStream implementation that redirects written data to the Logger system.
 * This class allows integration of traditional stream-based logging with the Hyphanet logging
 * framework by wrapping output operations and converting them to log messages.
 * <p>
 * Each write operation is prefixed with a configurable string and logged at a specified
 * priority level. The class handles both single-byte writes and bulk write operations,
 * converting the bytes to strings using a specified character encoding.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * // Create a logging output stream for debug messages
 * OutputStream logStream = new OutputStreamLogger(LogLevel.DEBUG, "[MyApp] ", "UTF-8");
 *
 * // Write to the stream - this will be logged as a debug message
 * logStream.write("Status update".getBytes());
 * }
 * </pre>
 */
public class OutputStreamLogger extends OutputStream {

    /**
     * Creates a new OutputStreamLogger with specified logging parameters.
     *
     * @param prio    The LogLevel at which messages will be logged
     * @param prefix  Text to prepend to each logged message
     * @param charset Character encoding to use when converting bytes to strings
     */
    public OutputStreamLogger(LogLevel prio, String prefix, String charset) {
        this.prio = prio;
        this.prefix = prefix;
        this.charset = charset;
    }

    /**
     * Writes a single byte to the log.
     * The byte is converted to a character and logged with the configured prefix
     * at the specified priority level.
     *
     * @param b The byte to write (cast to char)
     */
    @Override
    public void write(int b) {
        Logger.logStatic(this, prefix + (char) b, prio);
    }

    /**
     * Writes a portion of a byte array to the log.
     * The bytes are converted to a string using the configured charset and logged
     * with the prefix at the specified priority level.
     *
     * @param buf    The buffer containing the bytes to write
     * @param offset The start position in the buffer
     * @param length The number of bytes to write
     */
    @Override
    public void write(byte @NonNull [] buf, int offset, int length) {
        try {
            // FIXME use Charset/CharsetDecoder
            Logger.logStatic(this, prefix + new String(buf, offset, length, charset), prio);
        } catch (UnsupportedEncodingException e) {
            // Impossible. Nothing we can do safely here. :(
        }
    }

    /**
     * Writes an entire byte array to the log.
     * Delegates to write(byte[], int, int) using the full array length.
     *
     * @param buf The buffer containing the bytes to write
     */
    @Override
    public void write(byte @NonNull [] buf) {
        write(buf, 0, buf.length);
    }

    /**
     * The priority level at which messages will be logged
     */
    final LogLevel prio;
    /**
     * Text to prepend to each logged message
     */
    final String prefix;
    /**
     * Character encoding used to convert bytes to strings
     */
    final String charset;
}
