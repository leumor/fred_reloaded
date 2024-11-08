package hyphanet.support.logger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An abstract base class that implements core logging functionality and threshold management.
 * LoggerHook serves as the foundation for concrete logging implementations, providing:
 *
 * <ul>
 *   <li>Configurable logging thresholds at both global and package-specific levels</li>
 *   <li>Thread-safe logging operations</li>
 *   <li>Support for detailed threshold management per package/class</li>
 *   <li>Callback mechanism for threshold changes</li>
 * </ul>
 * <p>
 * Key features:
 * <ul>
 *   <li>Hierarchical logging levels (ERROR, WARNING, NORMAL, MINOR, DEBUG)</li>
 *   <li>Fine-grained control through detailed thresholds</li>
 *   <li>Thread-safe implementation using CopyOnWriteArrayList for callbacks</li>
 *   <li>Support for both object-context and class-context logging</li>
 * </ul>
 * <p>
 * Usage example:
 * <pre>
 * LoggerHook logger = new CustomLoggerHook(LogLevel.NORMAL);
 * logger.setDetailedThresholds("com.example:DEBUG,org.test:ERROR");
 * logger.log(this, "Operation completed", LogLevel.NORMAL);
 * </pre>
 *
 * @see Logger
 * @see LogLevel
 * @see DetailedThreshold
 */
public abstract class LoggerHook extends Logger {

    /**
     * Detailed threshold configurations for specific packages/classes
     */
    public DetailedThreshold[] detailedThresholds = new DetailedThreshold[0];

    /**
     * Creates a new LoggerHook with the specified threshold level.
     *
     * @param thresh The initial logging threshold
     */
    protected LoggerHook(LogLevel thresh) {
        this.threshold = thresh;
    }

    /**
     * Creates a new LoggerHook with a threshold specified by name.
     *
     * @param thresh String representation of the threshold level
     * @throws InvalidThresholdException if the threshold name is invalid
     */
    LoggerHook(String thresh) throws InvalidThresholdException {
        this.threshold = parseThreshold(thresh.toUpperCase());
    }

    @Override
    public void log(Object o, String message, LogLevel priority) {
        if (!instanceShouldLog(priority, o)) return;
        log(o, o == null ? null : o.getClass(),
                message, null, priority);
    }

    @Override
    public void log(Object o, String message, Throwable e,
                    LogLevel priority) {
        if (!instanceShouldLog(priority, o)) return;
        log(o, o == null ? null : o.getClass(), message, e, priority);
    }

    @Override
    public void log(Class<?> c, String message, LogLevel priority) {
        if (!instanceShouldLog(priority, c)) return;
        log(null, c, message, null, priority);
    }

    @Override
    public void log(Class<?> c, String message, Throwable e, LogLevel priority) {
        if (!instanceShouldLog(priority, c))
            return;
        log(null, c, message, e, priority);
    }

    /**
     * Determines if a message with the given priority level should be logged based on the current threshold.
     * This method provides a basic filtering mechanism that checks if a log message meets the
     * minimum threshold requirements.
     * <p>
     * The priority is accepted if it matches or exceeds the current threshold level according to
     * the following hierarchy (from highest to lowest priority):
     * <ul>
     * <li>ERROR
     * <li>WARNING
     * <li>NORMAL
     * <li>MINOR
     * <li>DEBUG
     * <li>MINIMAL
     * </ul>
     * <p>
     * For example:
     * <ul>
     * <li>If threshold is WARNING, messages with ERROR and WARNING levels are accepted
     * <li>If threshold is MINOR, messages with ERROR, WARNING, NORMAL, and MINOR levels are accepted
     * </li>
     *
     * @param prio The LogLevel priority to check against the current threshold
     * @return true if the priority matches or exceeds the current threshold, false otherwise
     * @see LogLevel#matchesThreshold(LogLevel)
     * @see #setThreshold(LogLevel)
     * @see #getThresholdNew()
     */
    public boolean acceptPriority(LogLevel prio) {
        return prio.matchesThreshold(threshold);
    }

    @Override
    public void setThreshold(LogLevel thresh) {
        this.threshold = thresh;
        notifyLogThresholdCallbacks();
    }

    @Override
    public LogLevel getThresholdNew() {
        return threshold;
    }

    @Override
    public void setThreshold(String symbolicThreshold) throws InvalidThresholdException {
        setThreshold(parseThreshold(symbolicThreshold));
    }

    /**
     * Returns a string representation of all currently configured detailed thresholds.
     * The detailed thresholds allow for fine-grained control over logging levels for
     * specific classes.
     *
     * <p>The returned string follows the format:
     * <pre>classname:LEVEL1,classname:LEVEL2,...</pre>
     * where:
     * <ul>
     *   <li>Each entry consists of a class name and its threshold level</li>
     *   <li>Entries are separated by commas</li>
     *   <li>Each threshold level is one of the {@link LogLevel} enum values</li>
     * </ul></p>
     *
     * <p>Example return values:
     * <ul>
     *   <li><code>""</code> - when no detailed thresholds are configured</li>
     *   <li><code>"classname:DEBUG"</code> - single threshold</li>
     *   <li><code>"classname1:DEBUG,classname2:ERROR"</code> - multiple thresholds</li>
     * </ul></p>
     *
     * <p>This method is thread-safe as it operates on a synchronized copy of the
     * detailed thresholds array.</p>
     *
     * @return A string representation of all detailed thresholds, or an empty string
     * if no detailed thresholds are configured
     * @see #setDetailedThresholds(String)
     * @see LogLevel
     * @see DetailedThreshold
     */
    public String getDetailedThresholds() {
        DetailedThreshold[] thresh = null;
        synchronized (this) {
            thresh = detailedThresholds;
        }
        if (thresh.length == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        for (DetailedThreshold t : thresh) {
            sb.append(t.section);
            sb.append(':');
            sb.append(t.dThreshold);
            sb.append(',');
        }
        // assert(sb.length() > 0); -- always true as thresh.length != 0
        // remove last ','
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    @Override
    public void setDetailedThresholds(String details) throws InvalidThresholdException {
        if (details == null)
            return;
        StringTokenizer st = new StringTokenizer(details, ",", false);
        ArrayList<DetailedThreshold> stuff = new ArrayList<DetailedThreshold>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.isEmpty())
                continue;
            int x = token.indexOf(':');
            if (x < 0)
                continue;
            if (x == token.length() - 1)
                continue;
            String section = token.substring(0, x);
            String value = token.substring(x + 1);
            stuff.add(new DetailedThreshold(section, parseThreshold(value.toUpperCase())));
        }
        DetailedThreshold[] newThresholds = new DetailedThreshold[stuff.size()];
        stuff.toArray(newThresholds);
        synchronized (this) {
            detailedThresholds = newThresholds;
        }
        notifyLogThresholdCallbacks();
    }

    @Override
    public boolean instanceShouldLog(LogLevel priority, Class<?> c) {
        DetailedThreshold[] thresholds;
        LogLevel thresh;
        synchronized (this) {
            thresholds = detailedThresholds;
            thresh = threshold;
        }
        if ((c != null) && (thresholds.length > 0)) {
            String cname = c.getName();
            for (DetailedThreshold dt : thresholds) {
                if (cname.startsWith(dt.section))
                    thresh = dt.dThreshold;
            }
        }
        return priority.matchesThreshold(thresh);
    }

    @Override
    public final boolean instanceShouldLog(LogLevel prio, Object o) {
        return instanceShouldLog(prio, o == null ? null : o.getClass());
    }

    @Override
    public final void instanceRegisterLogThresholdCallback(LogThresholdCallback ltc) {
        thresholdsCallbacks.add(ltc);

        // Call the new callback to avoid code duplication
        ltc.shouldUpdate();
    }

    @Override
    public final void instanceUnregisterLogThresholdCallback(LogThresholdCallback ltc) {
        thresholdsCallbacks.remove(ltc);
    }

    /**
     * Parses a string representation of a logging threshold into its corresponding LogLevel enum value.
     *
     * <p>This method converts a case-insensitive string threshold name into the corresponding
     * {@link LogLevel} enum value. The input string must exactly match one of the LogLevel
     * enum names (ignoring case).</p>
     *
     * <p>Valid threshold strings are:
     * <ul>
     *   <li><code>MINIMAL</code> - Highest priority, logs everything</li>
     *   <li><code>DEBUG</code> - Debug and higher priority messages</li>
     *   <li><code>MINOR</code> - Minor and higher priority messages</li>
     *   <li><code>NORMAL</code> - Normal and higher priority messages</li>
     *   <li><code>WARNING</code> - Warning and error messages only</li>
     *   <li><code>ERROR</code> - Error messages only</li>
     *   <li><code>NONE</code> - No logging</li>
     * </ul></p>
     *
     * <p>Example usage:
     * <pre>
     * LogLevel level = parseThreshold("DEBUG");  // Returns LogLevel.DEBUG
     * LogLevel level = parseThreshold("warning"); // Returns LogLevel.WARNING
     * </pre></p>
     *
     * @param threshold The string representation of the threshold level. Must not be null
     *                  and must match one of the {@link LogLevel} enum names (case-insensitive).
     * @return The corresponding {@link LogLevel} enum value
     * @throws InvalidThresholdException if the threshold string is null or does not match
     *                                   any valid LogLevel enum name
     * @see LogLevel
     * @see #setThreshold(String)
     * @see #setDetailedThresholds(String)
     */
    private LogLevel parseThreshold(String threshold) throws InvalidThresholdException {
        if (threshold == null) throw new InvalidThresholdException(threshold);
        try {
            return LogLevel.valueOf(threshold.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidThresholdException(threshold);
        }
    }

    /**
     * Notifies all registered callbacks when logging thresholds have changed.
     *
     * @see LogThresholdCallback
     * @see #instanceRegisterLogThresholdCallback(LogThresholdCallback)
     * @see #instanceUnregisterLogThresholdCallback(LogThresholdCallback)
     * @see #setThreshold(LogLevel)
     * @see #setDetailedThresholds(String)
     */
    private void notifyLogThresholdCallbacks() {
        for (LogThresholdCallback ltc : thresholdsCallbacks)
            ltc.shouldUpdate();
    }

    /**
     * A class that holds threshold configuration for a specific class hierarchy.
     * DetailedThreshold enables fine-grained logging control by allowing different
     * logging thresholds for different parts of the codebase.
     *
     * <p>Each DetailedThreshold instance represents a class and its
     * associated logging threshold level. When a log message is generated, the logger
     * checks if the message's source matches any DetailedThreshold prefixes and uses
     * the most specific matching threshold.</p>
     */
    public static final class DetailedThreshold {
        public DetailedThreshold(String section, LogLevel thresh) {
            this.section = section;
            this.dThreshold = thresh;
        }

        /**
         * The class name that this threshold applies to.
         */
        final String section;
        /**
         * The logging threshold level for classes matching this section prefix.
         * Messages with priority levels below this threshold will be filtered out
         * for matching classes.
         *
         * <p>This threshold overrides the global logger threshold for classes
         * that match the section prefix.</p>
         *
         * @see LogLevel
         */
        final LogLevel dThreshold;
    }

    /**
     * An exception thrown when an invalid logging threshold value is specified.
     * This exception indicates that a threshold string could not be parsed into
     * a valid {@link LogLevel} value.
     *
     * <p>This exception is thrown in scenarios such as:
     * <ul>
     *   <li>Null threshold string provided</li>
     *   <li>Empty threshold string provided</li>
     *   <li>Threshold string that doesn't match any {@link LogLevel} enum value</li>
     *   <li>Invalid format in detailed threshold specifications</li>
     * </ul></p>
     *
     * <p>Example scenarios that would throw this exception:
     * <pre>
     * // Invalid threshold name
     * logger.setThreshold("INVALID_LEVEL");
     *
     * // Invalid detailed threshold format
     * logger.setDetailedThresholds("com.example:INVALID");
     * </pre></p>
     *
     * @see LogLevel
     * @see LoggerHook#setThreshold(String)
     * @see LoggerHook#setDetailedThresholds(String)
     */
    public static class InvalidThresholdException extends Exception {
        InvalidThresholdException(String msg) {
            super(msg);
        }

        @Serial
        private static final long serialVersionUID = -1;
    }

    /**
     * Thread-safe list of callbacks for threshold changes
     */
    private final CopyOnWriteArrayList<LogThresholdCallback> thresholdsCallbacks = new CopyOnWriteArrayList<LogThresholdCallback>();

    /**
     * The global threshold level for this logger
     */
    protected LogLevel threshold;

}
