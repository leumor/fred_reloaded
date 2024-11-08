package hyphanet.support.logger;

import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class LoggerHook extends Logger {

    public DetailedThreshold[] detailedThresholds = new DetailedThreshold[0];

    protected LoggerHook(LogLevel thresh) {
        this.threshold = thresh;
    }

    LoggerHook(String thresh) throws InvalidThresholdException {
        this.threshold = parseThreshold(thresh.toUpperCase());
    }

    /**
     * Log a message
     *
     * @param o        The object where this message was generated.
     * @param source   The class where this message was generated.
     * @param message  A clear and verbose message describing the event
     * @param e        Logs this exception with the message.
     * @param priority The priority of the mesage, one of LogLevel.ERROR,
     *                 LogLevel.NORMAL, LogLevel.MINOR, or LogLevel.DEBUG.
     */
    @Override
    public abstract void log(
            Object o,
            Class<?> source,
            String message,
            Throwable e,
            LogLevel priority);

    /**
     * Log a message.
     *
     * @param o        The source object where this message was generated
     * @param message  A clear and verbose message describing the event
     * @param priority The priority of the mesage, one of LogLevel.ERROR,
     *                 LogLevel.NORMAL, LogLevel.MINOR, or LogLevel.DEBUG.
     **/
    @Override
    public void log(Object o, String message, LogLevel priority) {
        if (!instanceShouldLog(priority, o)) return;
        log(o, o == null ? null : o.getClass(),
                message, null, priority);
    }

    /**
     * Log a message with an exception.
     *
     * @param o        The source object where this message was generated.
     * @param message  A clear and verbose message describing the event.
     * @param e        Logs this exception with the message.
     * @param priority The priority of the mesage, one of LogLevel.ERROR,
     *                 LogLevel.NORMAL, LogLevel.MINOR, or LogLevel.DEBUG.
     * @see #log(Object o, String message, int priority)
     */
    @Override
    public void log(Object o, String message, Throwable e,
                    LogLevel priority) {
        if (!instanceShouldLog(priority, o)) return;
        log(o, o == null ? null : o.getClass(), message, e, priority);
    }

    /**
     * Log a message from static code.
     *
     * @param c        The class where this message was generated.
     * @param message  A clear and verbose message describing the event
     * @param priority The priority of the mesage, one of LogLevel.ERROR,
     *                 LogLevel.NORMAL, LogLevel.MINOR, or LogLevel.DEBUG.
     */
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

    private LogLevel parseThreshold(String threshold) throws InvalidThresholdException {
        if (threshold == null) throw new InvalidThresholdException(threshold);
        try {
            return LogLevel.valueOf(threshold.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidThresholdException(threshold);
        }
    }

    private void notifyLogThresholdCallbacks() {
        for (LogThresholdCallback ltc : thresholdsCallbacks)
            ltc.shouldUpdate();
    }

    public static final class DetailedThreshold {
        public DetailedThreshold(String section, LogLevel thresh) {
            this.section = section;
            this.dThreshold = thresh;
        }

        final String section;
        final LogLevel dThreshold;
    }

    public static class InvalidThresholdException extends Exception {
        InvalidThresholdException(String msg) {
            super(msg);
        }

        private static final long serialVersionUID = -1;
    }
    private final CopyOnWriteArrayList<LogThresholdCallback> thresholdsCallbacks = new CopyOnWriteArrayList<LogThresholdCallback>();
    protected LogLevel threshold;

}
