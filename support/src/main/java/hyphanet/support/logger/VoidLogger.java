/*
 * Created on Mar 18, 2004
 */
package hyphanet.support.logger;

/**
 * A null implementation of the Logger interface that discards all log messages.
 * This class implements the Null Object pattern for the logging system,
 * providing a no-op logger that can be used when logging is disabled or
 * not required.
 * <p>
 * Key characteristics:
 * <ul>
 * <li>Discards all log messages without processing
 * <li>Always returns false for shouldLog checks
 * <li>Returns LogLevel.NONE as threshold
 * <li>Ignores all configuration attempts
 * <li>Thread-safe by design (no state to protect)
 * </ul>
 * <p>
 * Common use cases:
 * <ul>
 * <li>Default logger before initialization
 * <li>Placeholder when logging is disabled
 * <li>Testing scenarios where logging is irrelevant
 * </ul>
 * <p>
 * Performance considerations:
 * <ul>
 * <li>Zero overhead for logging operations
 * <li>No memory allocation for log messages
 * <li>No I/O operations performed
 * </ul>
 *
 * @author Iakin
 * @see Logger
 * @see LoggerHook
 * @see LogLevel#NONE
 */
public class VoidLogger extends Logger {

    @Override
    public void log(Object o, Class<?> source, String message, Throwable e, LogLevel priority) {
    }

    @Override
    public void log(Object o, String message, LogLevel priority) {
    }

    @Override
    public void log(Object o, String message, Throwable e, LogLevel priority) {
    }

    @Override
    public void log(Class<?> c, String message, LogLevel priority) {
    }

    @Override
    public void log(Class<?> c, String message, Throwable e, LogLevel priority) {
    }

    public long minFlags() {
        return 0;
    }

    public long notFlags() {
        return 0;
    }

    public long anyFlags() {
        return 0;
    }

    @Override
    public boolean instanceShouldLog(LogLevel priority, Class<?> c) {
        return false;
    }

    @Override
    public boolean instanceShouldLog(LogLevel prio, Object o) {
        return false;
    }

    @Override
    public void setThreshold(LogLevel thresh) {
    }

    @Override
    public LogLevel getThresholdNew() {
        return LogLevel.NONE;
    }

    @Override
    public void setThreshold(String symbolicThreshold) {
    }

    @Override
    public void setDetailedThresholds(String details) {
    }

    @Override
    public final void instanceRegisterLogThresholdCallback(LogThresholdCallback ltc) {
    }

    @Override
    public final void instanceUnregisterLogThresholdCallback(LogThresholdCallback ltc) {
    }
}
