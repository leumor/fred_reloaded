package hyphanet.support.logger;

import hyphanet.support.logger.Logger.LogLevel;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * A logging implementation that distributes logging messages to multiple {@link LoggerHook} instances.
 * This class implements the Chain of Responsibility pattern for logging, allowing logging events to be
 * processed by multiple handlers in sequence.
 *
 * <p>This implementation is thread-safe and supports dynamic addition and removal of logging hooks
 * at runtime. It extends {@link LoggerHook} to allow for logger chaining, but care should be taken to
 * avoid creating cycles in the chain.</p>
 *
 * <p><strong>Usage example:</strong></p>
 * <pre>{@code
 * LoggerHookChain chain = new LoggerHookChain(LogLevel.NORMAL);
 * chain.addHook(new FileLoggerHook())
 *      .addHook(new ConsoleLoggerHook());
 * chain.log("Test message", LogLevel.NORMAL);
 * }</pre>
 *
 * @see LoggerHook
 * @see LogLevel
 */
public class LoggerHookChain extends LoggerHook {

    /**
     * Constructs a new logger chain with {@link LogLevel#NORMAL} threshold.
     */
    public LoggerHookChain() {
        this(LogLevel.NORMAL);
    }

    /**
     * Constructs a new logger chain with the specified threshold.
     *
     * @param threshold the minimum {@link LogLevel} for log messages to be processed
     */
    public LoggerHookChain(LogLevel threshold) {
        super(threshold);
        hooks = new CopyOnWriteArrayList<>();
    }

    /**
     * Constructs a new logger chain with a threshold specified by name.
     *
     * @param threshold string representation of the threshold level
     *
     * @throws InvalidThresholdException if the threshold name is invalid
     */
    public LoggerHookChain(String threshold) throws InvalidThresholdException {
        super(threshold);
        hooks = new CopyOnWriteArrayList<>();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation distributes the log message to all registered hooks.</p>
     */
    @Override
    public void log(Object o, Class<?> c, String msg, Throwable e, LogLevel priority) {
        for (LoggerHook hook : hooks) {
            hook.log(o, c, msg, e, priority);
        }
    }

    /**
     * Adds a new logging hook to the chain.
     *
     * @param hook the logging hook to add
     *
     * @return this chain instance for method chaining
     */
    public LoggerHookChain addHook(LoggerHook hook) {
        hooks.add(hook);
        return this;
    }

    /**
     * Removes a logging hook from the chain.
     *
     * @param hook the logging hook to remove
     *
     * @return this chain instance for method chaining
     */
    public LoggerHookChain removeHook(LoggerHook hook) {
        hooks.remove(hook);
        return this;
    }

    /**
     * Returns an unmodifiable list of all currently registered hooks.
     *
     * @return an unmodifiable list of logging hooks
     */
    public List<LoggerHook> getHooks() {
        return Collections.unmodifiableList(hooks);
    }

    /**
     * Thread-safe list of logging hooks that will receive log messages.
     */
    private final CopyOnWriteArrayList<LoggerHook> hooks;
}

