package hyphanet.support.logger;

import hyphanet.support.logger.Logger.LogLevel;

import java.io.Serial;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An abstract base class that implements core logging functionality and threshold management. LoggerHook
 * serves as the foundation for concrete logging implementations, providing:
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
public abstract class LoggerHook {

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
     *
     * @throws InvalidThresholdException if the threshold name is invalid
     */
    LoggerHook(String thresh) throws InvalidThresholdException {
        this.threshold = parseThreshold(thresh.toUpperCase());
    }

    /**
     * Logs a message with detailed context information at the specified priority level. This is the core
     * logging method that all other logging methods ultimately delegate to.
     *
     * @param o        The object where this message was generated, used for context and detailed
     *                 threshold matching. Maybe null for static contexts.
     * @param source   The class where this message was generated, used for detailed threshold matching
     *                 and logging context. If o is non-null, this should typically be o.getClass().
     * @param message  A clear and descriptive message describing the event. Should be non-null and
     *                 meaningful.
     * @param e        An optional Throwable to be logged with the message. May be null if there is no
     *                 associated exception.
     * @param priority The priority level for this message, determining if and how the message should be
     *                 logged based on current thresholds. Must be one of the LogLevel enum values.
     *
     * @see LogLevel
     * @see #log(Object, String, LogLevel)
     * @see #log(Class, String, LogLevel)
     */
    public abstract void log(
        Object o, Class<?> source, String message, Throwable e, LogLevel priority);

    /**
     * Logs a message from a source object at the specified priority level. This is a convenience method
     * that automatically extracts the class from the source object for logging context.
     *
     * @param o        The object generating the log message, used for context and detailed threshold
     *                 matching. Maybe null for static contexts.
     * @param message  A clear and descriptive message describing the event. Should be non-null and
     *                 meaningful.
     * @param priority The priority level for this message, determining if and how the message should be
     *                 logged based on current thresholds. Must be one of the LogLevel enum values.
     *
     * @see #log(Object, Class, String, Throwable, LogLevel)
     * @see LogLevel
     */
    public void log(Object o, String message, LogLevel priority) {
        if (!instanceShouldLog(priority, o)) {
            return;
        }
        log(o, o == null ? null : o.getClass(), message, null, priority);
    }

    /**
     * Logs a message with an associated exception at the specified priority level. This is a convenience
     * method that automatically extracts the class context from the source object.
     *
     * @param o        The object generating the log message, used for context and detailed threshold
     *                 matching. Maybe null for static contexts.
     * @param message  A clear and descriptive message about the event being logged. Should be non-null
     *                 and meaningful.
     * @param e        An optional Throwable to be logged with the message. May be null if there is no
     *                 associated exception.
     * @param priority The priority level for this message, determining if and how the message should be
     *                 logged based on current thresholds. Must be one of the LogLevel enum values.
     *
     * @see #log(Object, Class, String, Throwable, LogLevel)
     * @see LogLevel
     */
    public void log(
        Object o, String message, Throwable e, LogLevel priority) {
        if (!instanceShouldLog(priority, o)) {
            return;
        }
        log(o, o == null ? null : o.getClass(), message, e, priority);
    }

    /**
     * Logs a message from static code at the specified priority level. This method is specifically
     * designed for logging from static contexts where an object instance is not available.
     *
     * @param c        The class where this message was generated, used for detailed threshold matching
     *                 and logging context. Must not be null when detailed thresholds are in use.
     * @param message  A clear and descriptive message about the event being logged. Should be non-null
     *                 and meaningful.
     * @param priority The priority level for this message, determining if and how the message should be
     *                 logged based on current thresholds. Must be one of the LogLevel enum values.
     *
     * @see #log(Object, Class, String, Throwable, LogLevel)
     * @see LogLevel
     */
    public void log(Class<?> c, String message, LogLevel priority) {
        if (!instanceShouldLog(priority, c)) {
            return;
        }
        log(null, c, message, null, priority);
    }

    /**
     * Logs a message with an exception from static code at the specified priority level. This method is
     * specifically designed for logging from static contexts where an object instance is not available.
     *
     * @param c        The class where this message was generated, used for detailed threshold matching
     *                 and logging context. Must not be null when detailed thresholds are in use.
     * @param message  A clear and descriptive message about the event being logged. Should be non-null
     *                 and meaningful.
     * @param e        The Throwable to be logged with this message. Contains the stack trace and
     *                 additional error context. May be null if there is no associated exception.
     * @param priority The priority level for this message, determining if and how the message should be
     *                 logged based on current thresholds. Must be one of the LogLevel enum values.
     *
     * @see #log(Object, Class, String, Throwable, LogLevel)
     * @see LogLevel
     */
    public void log(Class<?> c, String message, Throwable e, LogLevel priority) {
        if (!instanceShouldLog(priority, c)) {
            return;
        }
        log(null, c, message, e, priority);
    }

    /**
     * Determines if a message with the given priority level should be logged based on the current
     * threshold. This method provides a basic filtering mechanism that checks if a log message meets the
     * minimum threshold requirements.
     * <p>
     * The priority is accepted if it matches or exceeds the current threshold level according to the
     * following hierarchy (from highest to lowest priority):
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
     *
     * @return true if the priority matches or exceeds the current threshold, false otherwise
     *
     * @see LogLevel#matchesThreshold(LogLevel)
     * @see #setThreshold(LogLevel)
     * @see #getThresholdNew()
     */
    public boolean acceptPriority(LogLevel prio) {
        return prio.matchesThreshold(threshold);
    }

    /**
     * Sets the global logging threshold level for this logger. Messages with priority levels lower than
     * this threshold will not be logged.
     *
     * <p><b>Example usage:</b></p>
     * <pre>
     * {@code
     * logger.setThreshold(LogLevel.DEBUG);  // Enable debug logging
     * logger.setThreshold(LogLevel.ERROR);  // Only log errors
     * logger.setThreshold(LogLevel.NONE);   // Disable all logging
     * }
     * </pre>
     *
     * @param thresh The new threshold level to set. Must be one of the {@link LogLevel} enum values.
     *               Using {@link LogLevel#NONE} will effectively disable all logging.
     *
     * @see LogLevel
     * @see #getThresholdNew()
     * @see #setDetailedThresholds(String)
     */
    public void setThreshold(LogLevel thresh) {
        this.threshold = thresh;
        notifyLogThresholdCallbacks();
    }

    /**
     * Returns the current logging threshold level for this logger. The threshold determines which
     * messages will be logged based on their priority. Messages with a priority level lower than the
     * threshold will be filtered out.
     *
     * @return The current LogLevel threshold.
     *
     * @see LogLevel
     * @see #setThreshold(LogLevel)
     * @see #setDetailedThresholds(String)
     */
    public LogLevel getThresholdNew() {
        return threshold;
    }

    /**
     * Sets the logging threshold using a symbolic string representation. This method allows for a more
     * user-friendly way to set thresholds compared to using LogLevel enums directly.
     *
     * @param symbolicThreshold A string representation of the desired threshold level. Must be one of
     *                          the LogLevel enum names (case-insensitive). Valid values are: "MINIMAL",
     *                          "DEBUG", "MINOR", "NORMAL", "WARNING", "ERROR", or "NONE".
     *
     * @throws InvalidThresholdException if the provided string does not correspond to a valid LogLevel
     *                                   enum value
     * @see LogLevel
     * @see #setThreshold(LogLevel)
     * @see #getThresholdNew()
     */
    public void setThreshold(String symbolicThreshold) throws InvalidThresholdException {
        setThreshold(parseThreshold(symbolicThreshold));
    }

    /**
     * Returns a string representation of all currently configured detailed thresholds. The detailed
     * thresholds allow for fine-grained control over logging levels for specific classes.
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
     * @return A string representation of all detailed thresholds, or an empty string if no detailed
     * thresholds are configured
     *
     * @see #setDetailedThresholds(String)
     * @see LogLevel
     * @see DetailedThreshold
     */
    public String getDetailedThresholds() {
        DetailedThreshold[] thresh = null;
        synchronized (this) {
            thresh = detailedThresholds;
        }
        if (thresh.length == 0) {
            return "";
        }
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

    /**
     * Sets detailed threshold levels for specific package/class hierarchies. This method allows
     * fine-grained control over logging levels by specifying different thresholds for different parts of
     * the codebase.
     *
     * @param details A comma-separated list of package:threshold pairs. Format:
     *                "classname:threshold,classname:threshold,..." Each threshold must be one of:
     *                MINIMAL, DEBUG, MINOR, NORMAL, WARNING, ERROR, or NONE. Null or empty string clears
     *                all detailed thresholds.
     *
     * @throws InvalidThresholdException if any threshold specification is invalid or if the details
     *                                   string is malformed
     * @see LogLevel
     * @see #setThreshold(LogLevel)
     * @see #getThresholdNew()
     */
    public void setDetailedThresholds(String details) throws InvalidThresholdException {
        if (details == null) {
            return;
        }
        StringTokenizer st = new StringTokenizer(details, ",", false);
        ArrayList<DetailedThreshold> stuff = new ArrayList<DetailedThreshold>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.isEmpty()) {
                continue;
            }
            int x = token.indexOf(':');
            if (x < 0) {
                continue;
            }
            if (x == token.length() - 1) {
                continue;
            }
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

    /**
     * Determines if a message should be logged for the given priority and class. This method checks both
     * the global threshold and any detailed thresholds that may be configured for specific classes.
     * <p>
     * The method performs the following checks:
     * <ol>
     * <li> Evaluates any detailed thresholds configured for the class's package
     * <li> Falls back to the global threshold if no detailed thresholds match
     * <li> Compares the message priority against the applicable threshold
     * </ol>
     *
     * @param priority The priority level of the message to be logged. Must be one of the LogLevel enum
     *                 values.
     * @param c        The class context for the log message. Used to check against detailed thresholds.
     *                 Maybe null for static contexts, in which case only the global threshold is
     *                 checked.
     *
     * @return true if a message with the given priority from the specified class should be logged, false
     * otherwise
     *
     * @see LogLevel
     * @see #setDetailedThresholds(String)
     * @see #setThreshold(LogLevel)
     */
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
                if (cname.startsWith(dt.section)) {
                    thresh = dt.dThreshold;
                }
            }
        }
        return priority.matchesThreshold(thresh);
    }

    /**
     * Determines if a message should be logged for the given priority and object. This is a convenience
     * method that automatically extracts the class from the object for threshold checking.
     * <p>
     * The method performs the following:
     * <ol>
     * <li> Extracts the class from the provided object if non-null
     * <li> Evaluate any detailed thresholds configured for the object's class package
     * <li> Falls back to the global threshold if no detailed thresholds match
     * <li> Compares the message priority against the applicable threshold
     * </ol>
     *
     * @param prio The priority level of the message to be logged. Must be one of the LogLevel enum
     *             values.
     * @param o    The object generating the log message. Used to determine the class context for
     *             threshold matching. Maybe null for static contexts, in which case only the global
     *             threshold is checked.
     *
     * @return true if a message with the given priority from the specified object's class should be
     * logged, false otherwise
     *
     * @see LogLevel
     * @see #instanceShouldLog(LogLevel, Class)
     * @see #setDetailedThresholds(String)
     */
    public boolean instanceShouldLog(LogLevel prio, Object o) {
        return instanceShouldLog(prio, o == null ? null : o.getClass());
    }

    /**
     * Registers a callback to be notified when logging thresholds change for this specific logger
     * instance (not with the global logger).
     * <p>
     * The callback will be immediately executed after registration and subsequently triggered whenever
     * threshold changes occur.
     *
     * @param ltc The LogThresholdCallback to register. Must not be null. The callback's shouldUpdate()
     *            method will be called immediately upon registration and after any future threshold
     *            changes.
     *
     * @see LogThresholdCallback
     * @see #instanceUnregisterLogThresholdCallback(LogThresholdCallback)
     * @see #setThreshold(LogLevel)
     * @see #setDetailedThresholds(String)
     */
    public void instanceRegisterLogThresholdCallback(LogThresholdCallback ltc) {
        thresholdsCallbacks.add(ltc);

        // Call the new callback to avoid code duplication
        ltc.shouldUpdate();
    }

    /**
     * Unregisters a log threshold callback from this specific logger instance. This method removes the
     * callback from the internal list of registered callbacks, preventing it from receiving future
     * threshold change notifications.
     * <p>
     * This method is thread-safe as it uses a CopyOnWriteArrayList internally for storing callbacks.
     * Multiple threads can safely call this method concurrently.
     *
     * @param ltc The LogThresholdCallback to unregister. Must not be null. If the callback is not
     *            currently registered, this method will have no effect.
     *
     * @see #instanceRegisterLogThresholdCallback(LogThresholdCallback)
     * @see LogThresholdCallback
     */
    public void instanceUnregisterLogThresholdCallback(LogThresholdCallback ltc) {
        thresholdsCallbacks.remove(ltc);
    }

    /**
     * Parses a string representation of a logging threshold into its corresponding LogLevel enum value.
     *
     * <p>This method converts a case-insensitive string threshold name into the corresponding
     * {@link LogLevel} enum value. The input string must exactly match one of the LogLevel enum names
     * (ignoring case).</p>
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
     * @param threshold The string representation of the threshold level. Must not be null and must match
     *                  one of the {@link LogLevel} enum names (case-insensitive).
     *
     * @return The corresponding {@link LogLevel} enum value
     *
     * @throws InvalidThresholdException if the threshold string is null or does not match any valid
     *                                   LogLevel enum name
     * @see LogLevel
     * @see #setThreshold(String)
     * @see #setDetailedThresholds(String)
     */
    private LogLevel parseThreshold(String threshold) throws InvalidThresholdException {
        if (threshold == null) {
            throw new InvalidThresholdException(threshold);
        }
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
        for (LogThresholdCallback ltc : thresholdsCallbacks) {
            ltc.shouldUpdate();
        }
    }

    /**
     * A class that holds threshold configuration for a specific class hierarchy. DetailedThreshold
     * enables fine-grained logging control by allowing different logging thresholds for different parts
     * of the codebase.
     *
     * <p>Each DetailedThreshold instance represents a class and its
     * associated logging threshold level. When a log message is generated, the logger checks if the
     * message's source matches any DetailedThreshold prefixes and uses the most specific matching
     * threshold.</p>
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
         * The logging threshold level for classes matching this section prefix. Messages with priority
         * levels below this threshold will be filtered out for matching classes.
         *
         * <p>This threshold overrides the global logger threshold for classes
         * that match the section prefix.</p>
         *
         * @see LogLevel
         */
        final LogLevel dThreshold;
    }

    /**
     * An exception thrown when an invalid logging threshold value is specified. This exception indicates
     * that a threshold string could not be parsed into a valid {@link LogLevel} value.
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
    private final CopyOnWriteArrayList<LogThresholdCallback> thresholdsCallbacks =
        new CopyOnWriteArrayList<LogThresholdCallback>();

    /**
     * The global threshold level for this logger
     */
    protected LogLevel threshold;

}
