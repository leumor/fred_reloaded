package hyphanet.support.logger;

import hyphanet.support.logger.FileLoggerHook.IntervalParseException;
import hyphanet.support.logger.LoggerHook.InvalidThresholdException;

import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.regex.PatternSyntaxException;

/**
 * A centralized logging facility that provides hierarchical logging levels, configurable thresholds, and
 * flexible logging destinations.
 * <p>
 * This thread-safe logging system supports:
 * <ul>
 * <li> Multiple logging levels (ERROR, WARNING, NORMAL, MINOR, DEBUG)
 * <li> Configurable thresholds per package/class
 * <li> Multiple output destinations through LoggerHooks
 * <li> Exception logging with stack traces
 * <li> Automatic log level field management
 * </ul>
 * <p>
 * Usage examples:
 * <pre>
 * // Basic setup with stdout logging
 * Logger.setupStdoutLogging(LogLevel.NORMAL, null);
 *
 * // Log messages at different levels
 * Logger.error(this, "Critical error occurred");
 * Logger.warning(MyClass.class, "Warning message");
 * Logger.debug(this, "Debug info", exception);
 *
 * // Configure detailed thresholds
 * Logger.setupStdoutLogging(LogLevel.NORMAL, "com.example:DEBUG,org.test:ERROR");
 *
 * // Check if logging is enabled
 * if (Logger.shouldLog(LogLevel.DEBUG, this)) {
 *     // Perform expensive debug operation
 * }
 * </pre>
 * <p>
 * Thread Safety:
 * All methods in this class are synchronized to ensure thread-safe operation
 * in multithreaded environments.
 *
 * @author Iakin
 * @see LogLevel
 * @see LoggerHook
 * @see FileLoggerHook
 */
public abstract class Logger {
    /**
     * Represents the available logging levels in descending order of severity. MINIMAL is the highest
     * priority, while NONE is used to disable logging.
     */
    public enum LogLevel {
        MINIMAL,
        /**
         * Used for detailed debugging information. Should only be enabled during development or
         * troubleshooting.
         */
        DEBUG, MINOR, NORMAL, WARNING, ERROR, NONE;

        /**
         * Determines if this log level should be included given a threshold. For being used to disable
         * logging completely. Do not use it as log level for actual log messages.
         *
         * @param threshold The minimum level to log
         *
         * @return true if this level should be logged given the threshold
         */
        public boolean matchesThreshold(LogLevel threshold) {
            return this.ordinal() >= threshold.ordinal();
        }
    }

    /**
     * Provides OS-specific thread and process information for Linux systems. This utility class offers
     * methods to access process IDs (PID) and parent process IDs (PPID) by parsing the /proc
     * filesystem.
     * <p>
     * System Requirements:
     * <ul>
     * <li> Only works on Linux systems
     * <li> Requires procfs to be mounted and accessible
     * <li> Needs read permissions for /proc/self/stat
     * </ul>
     *
     * @see #getPID(Object)
     * @see #getPPID(Object)
     * @see #logPID(Object)
     * @see #logPPID(Object)
     */
    public final static class OSThread {

        /**
         * Gets the current thread's process ID (PID) on Linux systems. This method provides access to
         * the process ID by parsing /proc/self/stat, avoiding the need for JNI calls.
         *
         * @param o The object requesting the PID, used for logging context if errors occur during PID
         *          retrieval. May be null, though this will limit error reporting capabilities.
         *
         * @return The process ID of the current thread, or -1 if:
         * <ul>
         * <li> PID retrieval is disabled
         * <li> Running on non-Linux system
         * <li> procfs is not mounted
         * <li> Insufficient permissions
         * <li> Parse error occurs
         * </ul>
         *
         * @see #getPIDFromProcSelfStat(Object)
         * @see #logPID(Object)
         */
        public synchronized static int getPID(Object o) {
            if (!getPIDEnabled) {
                return -1;
            }
            return getPIDFromProcSelfStat(o);
        }

        /**
         * Gets the parent process ID (PPID) of the current thread on Linux systems. This method provides
         * access to the parent process ID by parsing /proc/self/stat.
         *
         * @param o The object requesting the PPID, used for logging context if errors occur during PPID
         *          retrieval. May be null, though this will limit error reporting capabilities.
         *
         * @return The parent process ID of the current thread, or -1 if:
         * <ul>
         * <li> PPID retrieval is disabled
         * <li> Running on non-Linux system
         * <li> procfs is not mounted
         * <li> Insufficient permissions
         * <li> Parse error occurs
         * </ul>
         *
         * @see #getPID(Object)
         * @see #getFieldFromProcSelfStat(int, Object)
         */
        public synchronized static int getPPID(Object o) {
            if (!getPPIDEnabled) {
                return -1;
            }
            return getPPIDFromProcSelfStat(o);
        }

        /**
         * Retrieves a specific field from the /proc/self/stat file on Linux systems. This method parses
         * the process status information file to extract individual fields.
         * <p>
         * The /proc/self/stat file contains space-separated values with process information: - Field 0:
         * Process ID (PID) - Field 3: Parent Process ID (PPID) - And other process statistics
         *
         * @param fieldNumber The zero-based index of the field to retrieve from /proc/self/stat
         * @param o           The object requesting the field value, used for logging context if errors
         *                    occur during retrieval. May be null, though this will limit error reporting
         *                    capabilities.
         *
         * @return The requested field value as a String, or null if:
         * <ul>
         * <li> procfs is not enabled/available
         * <li> /proc/self/stat cannot be read
         * <li> File parsing fails
         * <li> Requested field is not available
         * </ul>
         *
         * @see #getPID(Object)
         * @see #getPPID(Object)
         */
        public synchronized static String getFieldFromProcSelfStat(int fieldNumber, Object o) {

            if (!procSelfStatEnabled) {
                return null;
            }

            // read /proc/self/stat and parse for the specified field
            File procFile = new File("/proc/self/stat");
            if (!procFile.exists()) {
                return null;
            }

            try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(procFile), StandardCharsets.ISO_8859_1))) {
                String readLine = br.readLine();
                if (readLine != null) {
                    try {

                        String[] procFields = readLine.trim().split(" ");
                        if (procFields.length >= 4) {
                            return procFields[fieldNumber];
                        }
                    } catch (PatternSyntaxException e) {
                        error(o, "Caught PatternSyntaxException in readLine.trim().split(\" \") of " +
                                 "OSThread" + ".getFieldFromProcSelfStat() while parsing '" + readLine +
                                 "'", e);
                    }
                }
            } catch (FileNotFoundException e) {
                logStatic(o, "'/proc/self/stat' not found", logToFileVerbosity);
                procSelfStatEnabled = false;
            } catch (IOException e) {
                error(o, "Caught IOException in br.readLine() of OSThread.getFieldFromProcSelfStat()",
                      e);
            }
            return null;
        }

        /**
         * Retrieves the process ID (PID) from /proc/self/stat on Linux systems. This method provides a
         * JNI-free way to access the process ID by parsing the first field of the proc filesystem
         * entry.
         *
         * @param o The object requesting the PID, used for logging context if errors occur during PID
         *          retrieval. May be null, though this will limit error reporting capabilities.
         *
         * @return The process ID as an integer, or -1 if:
         * <ul>
         * <li> PID retrieval is disabled
         * <li> procfs is not enabled/available
         * <li> /proc/self/stat cannot be read
         * <li> PID field cannot be parsed as an integer
         * </ul>
         *
         * @see #getPID(Object)
         * @see #getFieldFromProcSelfStat(int, Object)
         */
        public synchronized static int getPIDFromProcSelfStat(Object o) {
            int pid = -1;

            if (!getPIDEnabled) {
                return -1;
            }
            if (!procSelfStatEnabled) {
                return -1;
            }
            String pidString = getFieldFromProcSelfStat(0, o);
            if (null == pidString) {
                return -1;
            }
            try {
                pid = Integer.parseInt(pidString.trim());
            } catch (NumberFormatException e) {
                error(o, "Caught NumberFormatException in Integer.parseInt() of OSThread" +
                         ".getPIDFromProcSelfStat() while " + "parsing '" + pidString + "'", e);
            }
            return pid;
        }

        /**
         * Retrieves the parent process ID (PPID) from /proc/self/stat on Linux systems. This method
         * provides a JNI-free way to access the parent process ID by parsing field index 3 of the proc
         * filesystem entry.
         *
         * @param o The object requesting the PPID, used for logging context if errors occur during PPID
         *          retrieval. May be null, though this will limit error reporting capabilities.
         *
         * @return The parent process ID as an integer, or -1 if:
         * <ul>
         * <li> PPID retrieval is disabled
         * <li> procfs is not enabled/available
         * <li> /proc/self/stat cannot be read
         * <li> PPID field cannot be parsed as an integer
         * </ul>
         *
         * @see #getPPID(Object)
         * @see #getFieldFromProcSelfStat(int, Object)
         */
        public synchronized static int getPPIDFromProcSelfStat(Object o) {
            int ppid = -1;

            if (!getPPIDEnabled) {
                return -1;
            }
            if (!procSelfStatEnabled) {
                return -1;
            }
            String ppidString = getFieldFromProcSelfStat(3, o);
            if (null == ppidString) {
                return -1;
            }
            try {
                ppid = Integer.parseInt(ppidString.trim());
            } catch (NumberFormatException e) {
                error(o, "Caught NumberFormatException in Integer.parseInt() of OSThread" +
                         ".getPPIDFromProcSelfStat() while" + " parsing '" + ppidString + "'", e);
            }
            return ppid;
        }

        /**
         * Logs the current thread's process ID (PID) with additional context information. This method
         * combines PID retrieval with immediate logging of the result.
         *
         * @param o The object requesting the logging, used for context in log messages. May be null,
         *          though this will limit the context information in the log output.
         *
         * @return The process ID that was logged, or -1 if:
         * <ul>
         * <li>PID logging is disabled
         * <li>Running on non-Linux system
         * <li>procfs is not mounted
         * <li>Insufficient permissions
         * <li>Parse error occurs
         * </ul>
         *
         * @see #getPID(Object)
         * @see #logPPID(Object)
         */
        public synchronized static int logPID(Object o) {
            if (!getPIDEnabled) {
                return -1;
            }
            int pid = getPID(o);
            String msg;
            if (-1 != pid) {
                msg = "This thread's OS PID is " + pid;
            } else {
                msg = "This thread's OS PID could not be determined";
            }
            if (logToStdOutEnabled) {
                System.out.println(msg + ": " + o);
            }
            if (logToFileEnabled) {
                logStatic(o, msg, logToFileVerbosity);
            }
            return pid;
        }

        /**
         * Logs the current thread's parent process ID (PPID) with additional context information. This
         * method combines PPID retrieval with immediate logging of the result.
         *
         * @param o The object requesting the logging, used for context in log messages. May be null,
         *          though this will limit the context information in the log output.
         *
         * @return The parent process ID that was logged, or -1 if:
         * <ul>
         * <li>PPID logging is disabled
         * <li>Running on non-Linux system
         * <li>procfs is not mounted
         * <li>Insufficient permissions
         * <li>Parse error occurs
         * </ul>
         *
         * @see #getPPID(Object)
         * @see #logPID(Object)
         */
        public synchronized static int logPPID(Object o) {
            if (!getPPIDEnabled) {
                return -1;
            }
            int ppid = getPPID(o);
            String msg;
            if (-1 != ppid) {
                msg = "This thread's OS PPID is " + ppid;
            } else {
                msg = "This thread's OS PPID could not be determined";
            }
            if (logToStdOutEnabled) {
                System.out.println(msg + ": " + o);
            }
            if (logToFileEnabled) {
                logStatic(o, msg, logToFileVerbosity);
            }
            return ppid;
        }

        private static final boolean getPIDEnabled = false;
        private static final boolean getPPIDEnabled = false;
        private static final boolean logToFileEnabled = false;
        private static final LogLevel logToFileVerbosity = LogLevel.DEBUG;
        private static final boolean logToStdOutEnabled = false;
        private static boolean procSelfStatEnabled = false;
    }

    /**
     * Sets up logging to standard output with the specified threshold level and detailed thresholds.
     *
     * @param level  The minimum logging level to output
     * @param detail Optional detailed threshold settings in format "classname:threshold,..."
     *
     * @return The created FileLoggerHook instance
     *
     * @throws InvalidThresholdException if the threshold level is invalid
     * @throws SecurityException         if there are insufficient permissions
     */
    public synchronized static FileLoggerHook setupStdoutLogging(
        int buildNumber, LogLevel level, String detail) throws InvalidThresholdException {
        setupChain();
        logger.setThreshold(level);
        logger.setDetailedThresholds(detail);
        FileLoggerHook fh;
        try {
            fh = new FileLoggerHook.Builder(buildNumber, "d (c, t, p): m",
                                            "MMM dd, yyyy HH:mm:ss:SSS").threshold(level.name()).build();
        } catch (IntervalParseException | IOException e) {
            // Impossible
            throw new Error(e);
        }
        if (detail != null) {
            fh.setDetailedThresholds(detail);
        }
        ((LoggerHookChain) logger).addHook(fh);
        fh.start();
        return fh;
    }

    /**
     * Creates and initializes the global logging chain that manages multiple logging destinations. This
     * method is automatically called by setup methods like setupStdoutLogging(), but can also be called
     * explicitly when manual logger configuration is needed.
     * <p>
     * The LoggerHookChain allows:
     * <ul>
     * <li> Multiple logging destinations to receive the same log messages
     * <li> Dynamic addition and removal of logging hooks
     * <li> Centralized threshold management
     * <li> Unified message distribution
     * </ul>
     * <p>
     * This method is synchronized and safe to call from multiple threads. However, it should
     * typically only be called once during application initialization.
     * <p>
     * Usage Context:
     * <ul>
     * <li> Called automatically by setupStdoutLogging()
     * <li> Should be called before adding any custom LoggerHooks
     * <li> Safe to call multiple times (will reset the chain if already exists)
     * </ul>
     * <p>
     * Example:
     * <pre>
     * Logger.setupChain();
     * LoggerHookChain chain = (LoggerHookChain)Logger.logger;
     * chain.addHook(new CustomLoggerHook());
     * </pre>
     *
     * @see LoggerHookChain
     * @see #setupStdoutLogging(int, LogLevel, String)
     */
    public synchronized static void setupChain() {
        logger = new LoggerHookChain();
    }

    public synchronized static void debug(Class<?> c, String s) {
        logger.log(c, s, LogLevel.DEBUG);
    }

    // These methods log messages at various priorities using the global logger.

    public synchronized static void debug(Class<?> c, String s, Throwable t) {
        logger.log(c, s, t, LogLevel.DEBUG);
    }

    public synchronized static void debug(Object o, String s) {
        logger.log(o, s, LogLevel.DEBUG);
    }

    /**
     * Logs a debug message for the specified object. The message will only be logged if the current
     * threshold allows DEBUG level messages.
     *
     * @param o The object generating the log message (used for context)
     * @param s The message to log
     * @param t Optional throwable to log with the message
     */
    public synchronized static void debug(Object o, String s, Throwable t) {
        logger.log(o, s, t, LogLevel.DEBUG);
    }

    /**
     * Logs a message at the ERROR level.
     *
     * @param c The class where this message was generated
     * @param s The message to log
     */
    public synchronized static void error(Class<?> c, String s) {
        logger.log(c, s, LogLevel.ERROR);
    }

    /**
     * Logs a message with an exception at the ERROR level.
     *
     * @param c The class where this message was generated
     * @param s The message to log
     * @param t The throwable to include in the log
     */
    public synchronized static void error(Class<?> c, String s, Throwable t) {
        logger.log(c, s, t, LogLevel.ERROR);
    }

    public synchronized static void error(Object o, String s) {
        logger.log(o, s, LogLevel.ERROR);
    }

    public synchronized static void error(Object o, String s, Throwable e) {
        logger.log(o, s, e, LogLevel.ERROR);
    }

    public synchronized static void minor(Class<?> c, String s) {
        logger.log(c, s, LogLevel.MINOR);
    }

    public synchronized static void minor(Object o, String s) {
        logger.log(o, s, LogLevel.MINOR);
    }

    public synchronized static void minor(Object o, String s, Throwable t) {
        logger.log(o, s, t, LogLevel.MINOR);
    }

    /**
     * Logs a message at the MINOR level.
     *
     * @param c The class where this message was generated
     * @param s The message to log
     * @param t The throwable to include in the log
     */
    public synchronized static void minor(Class<?> c, String s, Throwable t) {
        logger.log(c, s, t, LogLevel.MINOR);
    }

    public synchronized static void normal(Object o, String s) {
        logger.log(o, s, LogLevel.NORMAL);
    }

    public synchronized static void normal(Object o, String s, Throwable t) {
        logger.log(o, s, t, LogLevel.NORMAL);
    }

    public synchronized static void normal(Class<?> c, String s) {
        logger.log(c, s, LogLevel.NORMAL);
    }

    public synchronized static void normal(Class<?> c, String s, Throwable t) {
        logger.log(c, s, t, LogLevel.NORMAL);
    }

    public synchronized static void warning(Class<?> c, String s) {
        logger.log(c, s, LogLevel.WARNING);
    }

    public synchronized static void warning(Class<?> c, String s, Throwable t) {
        logger.log(c, s, t, LogLevel.WARNING);
    }

    public synchronized static void warning(Object o, String s) {
        logger.log(o, s, LogLevel.WARNING);
    }

    public synchronized static void warning(Object o, String s, Throwable e) {
        logger.log(o, s, e, LogLevel.WARNING);
    }

    /**
     * Logs a static message with a specific priority level. This method is useful when logging from
     * static contexts.
     *
     * @param o    The object or class generating the log
     * @param s    The message to log
     * @param prio The priority level for the message
     */
    public synchronized static void logStatic(Object o, String s, LogLevel prio) {
        logger.log(o, s, prio);
    }

    public synchronized static void logStatic(Object o, String s, Throwable e, LogLevel prio) {
        logger.log(o, s, e, prio);
    }

    /**
     * Checks if messages at the given priority level for the specified class would be logged.
     *
     * @param priority The logging priority level to check
     * @param c        The class to check logging for
     *
     * @return true if messages would be logged at this level for this class
     */
    public static boolean shouldLog(LogLevel priority, Class<?> c) {
        return logger.instanceShouldLog(priority, c);
    }

    /**
     * Would a message concerning the given object be logged at the given priority by the global logger?
     */
    public static boolean shouldLog(LogLevel priority, Object o) {
        return shouldLog(priority, o.getClass());
    }

    /**
     * Registers a callback to be notified when logging thresholds change. The callback will be
     * immediately called after registration and subsequently whenever the logging thresholds are
     * modified in a way that affects the associated class's logging behavior.
     * <p>
     * The callback remains registered until explicitly unregistered or until the associated class is
     * unloaded by the JVM (if the callback holds a WeakReference to the class).
     * <p>
     * Example usage:
     * <pre>
     * LogThresholdCallback callback = new LogThresholdCallback() {
     *     public void shouldUpdate() {
     *         // Update logging flags based on current thresholds
     *         updateLoggingFlags();
     *     }
     * };
     * Logger.registerLogThresholdCallback(callback);
     * </pre>
     * <p>
     * This method is thread-safe and can be called from any thread. The callback will be stored in a
     * CopyOnWriteArrayList to ensure thread-safe iteration.
     *
     * @param ltc The callback to register. Must not be null.
     *
     * @see #unregisterLogThresholdCallback(LogThresholdCallback)
     * @see LogThresholdCallback
     */
    public static void registerLogThresholdCallback(LogThresholdCallback ltc) {
        logger.instanceRegisterLogThresholdCallback(ltc);
    }

    /**
     * Unregisters a previously registered LogThresholdCallback from receiving threshold change
     * notifications. This method should be called when the callback is no longer needed to prevent
     * memory leaks and unnecessary callback executions.
     * <p>
     * This method is thread-safe as it uses a CopyOnWriteArrayList internally for storing callbacks[2].
     * Multiple threads can safely call this method concurrently.
     * <p>
     * Example usage:
     * <pre>
     * LogThresholdCallback callback = new LogThresholdCallback() {
     *     public void shouldUpdate() {
     *         // Callback implementation
     *     }
     * };
     * Logger.registerLogThresholdCallback(callback);
     *
     * // When no longer needed:
     * Logger.unregisterLogThresholdCallback(callback);
     * </pre>
     * <p>
     * Note: - If the callback is not currently registered, this method will have no effect - The same
     * callback instance can be unregistered multiple times safely - After unregistering, the callback
     * will no longer receive threshold updates
     *
     * @param ltc The callback to unregister. Must not be null.
     *
     * @see #registerLogThresholdCallback(LogThresholdCallback)
     * @see LogThresholdCallback
     */
    public static void unregisterLogThresholdCallback(LogThresholdCallback ltc) {
        logger.instanceUnregisterLogThresholdCallback(ltc);
    }

    /**
     * Registers a class for automatic log level field updates. The class must have static boolean fields
     * named 'logMINOR' and optionally 'logDEBUG'. These fields will be automatically updated when
     * logging thresholds change.
     *
     * @param clazz The class to register
     */
    public static void registerClass(final Class<?> clazz) {
        LogThresholdCallback ltc = new LogThresholdCallback() {
            @Override
            public void shouldUpdate() {
                Class<?> clazz = ref.get();
                if (clazz == null) {    // class unloaded
                    unregisterLogThresholdCallback(this);
                    return;
                }

                boolean done = false;
                try {
                    Field logMINOR_Field = clazz.getDeclaredField("logMINOR");
                    if ((logMINOR_Field.getModifiers() & Modifier.STATIC) != 0) {
                        logMINOR_Field.setAccessible(true);
                        logMINOR_Field.set(null, shouldLog(LogLevel.MINOR, clazz));
                    }
                    done = true;
                } catch (SecurityException | NoSuchFieldException | IllegalArgumentException |
                         IllegalAccessException ignored) {
                }

                try {
                    Field logDEBUG_Field = clazz.getDeclaredField("logDEBUG");
                    if ((logDEBUG_Field.getModifiers() & Modifier.STATIC) != 0) {
                        logDEBUG_Field.setAccessible(true);
                        logDEBUG_Field.set(null, shouldLog(LogLevel.DEBUG, clazz));
                    }
                    done = true;
                } catch (SecurityException | NoSuchFieldException | IllegalArgumentException |
                         IllegalAccessException ignored) {
                }

                if (!done) {
                    Logger.error(this, "No log level field for " + clazz);
                }
            }

            final WeakReference<Class<?>> ref = new WeakReference<Class<?>>(clazz);
        };

        registerLogThresholdCallback(ltc);
    }

    /**
     * Reports a fatal error and exits the application.
     *
     * @param cause   The object or class that encountered the fatal error
     * @param retcode The exit code to use when terminating
     * @param message The error message describing the fatal condition
     */
    public static void fatal(Object cause, int retcode, String message) {
        error(cause, message);
        System.exit(retcode);
    }

    /**
     * Add a logger hook to the global logger hook chain. Messages which are not filtered out by the
     * global logger hook chain's thresholds will be passed to this logger.
     *
     * @param logger2 The logger hook to add to the chain
     */
    public synchronized static void globalAddHook(LoggerHook logger2) {
        if (logger instanceof VoidLoggerHook) {
            setupChain();
        }
        ((LoggerHookChain) logger).addHook(logger2);
    }

    /**
     * Sets the global logging threshold. Messages below this threshold will be ignored.
     *
     * @param i The new threshold level
     */
    public synchronized static void globalSetThreshold(LogLevel i) {
        logger.setThreshold(i);
    }

    /**
     * Retrieves the current global logging threshold.
     *
     * @return The current threshold level
     */
    public synchronized static LogLevel globalGetThresholdNew() {
        return logger.getThresholdNew();
    }

    /**
     * Remove a logger hook from the global logger hook chain.
     */
    public synchronized static void globalRemoveHook(LoggerHook hook) {
        if (logger instanceof LoggerHookChain) {
            ((LoggerHookChain) logger).removeHook(hook);
        } else {
            System.err.println("Cannot remove hook: " + hook + " global logger is " + logger);
        }
    }

    /**
     * Destroys the current logger chain if it has no hooks attached by replacing it with a VoidLogger.
     * This method helps maintain a clean logging state by removing empty logger chains that are no
     * longer in use.
     * <p>
     * This method is synchronized and safe to call from multiple threads. It performs an atomic
     * check-and-destroy operation to prevent race conditions.
     * <p>
     * Example:
     * <pre>
     * LoggerHookChain chain = (LoggerHookChain)Logger.logger;
     * chain.removeHook(lastHook);
     * Logger.destroyChainIfEmpty();
     * </pre>
     *
     * @see LoggerHookChain
     * @see #setupChain()
     */
    public synchronized static void destroyChainIfEmpty() {
        if (logger instanceof VoidLoggerHook) {
            return;
        }
        if ((logger instanceof LoggerHookChain) && (((LoggerHookChain) logger).getHooks().isEmpty())) {
            logger = new VoidLoggerHook();
        }
    }

    /**
     * Gets the global logger hook chain, creating it if necessary.
     * <p>
     * This method ensures that a LoggerHookChain is available for use. If the current logger is already
     * a LoggerHookChain, it returns it. Otherwise, it creates a new LoggerHookChain and transfers any
     * existing non-VoidLogger to the new chain.
     * <p>
     * This method is synchronized to ensure thread-safety during logger initialization.
     *
     * @return The global LoggerHookChain instance
     *
     * @throws IllegalStateException if the old logger is neither a VoidLogger nor a LoggerHook
     */
    public synchronized static LoggerHookChain getChain() {
        if (!(logger instanceof LoggerHookChain)) {
            LoggerHook oldLogger = logger;
            if (!(oldLogger instanceof VoidLoggerHook)) {
                if (oldLogger == null) {
                    throw new IllegalStateException(
                        "The old logger is not a VoidLogger and is not a LoggerHook either!");
                }
            }
            setupChain();
            if (!(oldLogger instanceof VoidLoggerHook)) {
                ((LoggerHookChain) logger).addHook(oldLogger);
            }
        }
        return (LoggerHookChain) logger;
    }

    /**
     * Single global LoggerHook.
     */
    static LoggerHook logger = new VoidLoggerHook();
}
