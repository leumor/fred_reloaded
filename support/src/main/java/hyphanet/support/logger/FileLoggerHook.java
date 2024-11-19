package hyphanet.support.logger;

import hyphanet.support.io.FileUtil;
import hyphanet.support.logger.Logger.LogLevel;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.zip.GZIPOutputStream;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Converted the old StandardLogger to Ian's loggerhook interface.
 *
 * @author oskar
 */
public class FileLoggerHook extends LoggerHook implements Closeable {

    /**
     * Verbosity types
     */
    public static final int DATE = 1, CLASS = 2, HASHCODE = 3, THREAD = 4, PRIORITY = 5, MESSAGE = 6,
        UNAME = 7;

    public static class Builder {
        public Builder(int buildNumber, String fmt, String dfmt) {
            this.buildNumber = buildNumber;
            this.fmt = fmt;
            this.dfmt = dfmt;
        }

        public Builder threshold(String threshold) {
            this.threshold = LogLevel.valueOf(threshold.toUpperCase());
            return this;
        }

        public Builder threshold(LogLevel threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder logRotateInterval(String interval) {
            this.logRotateInterval = interval;
            return this;
        }

        public Builder assumeWorking(boolean assumeWorking) {
            this.assumeWorking = assumeWorking;
            return this;
        }

        public Builder logOverwrite(boolean overwrite) {
            this.logOverwrite = overwrite;
            return this;
        }

        public Builder maxOldLogfilesDiskUsage(long usage) {
            this.maxOldLogfilesDiskUsage = usage;
            return this;
        }

        public Builder maxListSize(int size) {
            this.maxListSize = size;
            return this;
        }

        public Builder outputStream(OutputStream stream) {
            this.stream = stream;
            return this;
        }

        public Builder baseFilename(String filename, boolean rotate) {
            this.baseFilename = filename;
            this.rotate = rotate;
            return this;
        }

        public FileLoggerHook build() throws IOException, IntervalParseException {
            return new FileLoggerHook(this);
        }

        // Required parameters
        private final int buildNumber;
        private final String fmt;
        private final String dfmt;

        // Optional parameters with defaults
        private LogLevel threshold = LogLevel.NORMAL;
        private String logRotateInterval = "HOUR";
        private boolean assumeWorking = true;
        private boolean logOverwrite = false;
        private long maxOldLogfilesDiskUsage = -1;
        private int maxListSize = 10000;
        private OutputStream stream = null;
        private String baseFilename = null;
        private boolean rotate = false;
    }

    public static class IntervalParseException extends Exception {

        public IntervalParseException(String string) {
            super(string);
        }

        private static final long serialVersionUID = 69847854744673572L;

    }

    static {
        Logger.registerLogThresholdCallback(new LogThresholdCallback() {
            @Override
            public void shouldUpdate() {
                logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
            }
        });
    }

    static {
        uname = "unknown";
    }

    private FileLoggerHook(Builder builder) throws IOException, IntervalParseException {
        super(builder.threshold);

        // Initialize basic fields
        this.buildNumber = builder.buildNumber;
        this.maxOldLogfilesDiskUsage = builder.maxOldLogfilesDiskUsage;
        this.logOverwrite = builder.logOverwrite;
        if (builder.rotate) {
            this.baseFilename = builder.baseFilename;
        }

        // Initialize queue
        this.MAX_LIST_SIZE = builder.maxListSize;
        this.list = new ArrayBlockingQueue<>(MAX_LIST_SIZE);

        // Set formats
        setDateFormat(builder.dfmt);
        setLogFormat(builder.fmt);
        setInterval(builder.logRotateInterval);

        // Check streams if needed
        if (!builder.assumeWorking) {
            checkStdStreams();
        }

        // Initialize WriterThread
        if (builder.stream != null) {
            wt = new WriterThread(builder.stream);
        } else if (baseFilename != null) {
            wt = new WriterThread(
                new BufferedOutputStream(new FileOutputStream(baseFilename, !builder.logOverwrite),
                                         65536));
        } else {
            wt = new WriterThread(null);
        }
    }

    public static int numberOf(char c) {
        switch (c) {
            case 'd':
                return DATE;
            case 'c':
                return CLASS;
            case 'h':
                return HASHCODE;
            case 't':
                return THREAD;
            case 'p':
                return PRIORITY;
            case 'm':
                return MESSAGE;
            case 'u':
                return UNAME;
            default:
                return 0;
        }
    }

    public void setMaxListBytes(long len) {
        synchronized (list) {
            MAX_LIST_BYTES = len;
            LIST_WRITE_THRESHOLD = MAX_LIST_BYTES / 4;
        }
    }

    public void setInterval(FileLoggerHook this, String intervalName) throws IntervalParseException {
        StringBuilder sb = new StringBuilder(intervalName.length());
        for (int i = 0; i < intervalName.length(); i++) {
            char c = intervalName.charAt(i);
            if (!Character.isDigit(c)) {
                break;
            }
            sb.append(c);
        }
        if (!sb.isEmpty()) {
            String prefix = sb.toString();
            intervalName = intervalName.substring(prefix.length());
            intervalMultiplier = Integer.parseInt(prefix);
        } else {
            intervalMultiplier = 1;
        }
        if (intervalName.endsWith("S")) {
            intervalName = intervalName.substring(0, intervalName.length() - 1);
        }
        if (intervalName.equalsIgnoreCase("MINUTE")) {
            interval = Calendar.MINUTE;
        } else if (intervalName.equalsIgnoreCase("HOUR")) {
            interval = Calendar.HOUR;
        } else if (intervalName.equalsIgnoreCase("DAY")) {
            interval = Calendar.DAY_OF_MONTH;
        } else if (intervalName.equalsIgnoreCase("WEEK")) {
            interval = Calendar.WEEK_OF_YEAR;
        } else if (intervalName.equalsIgnoreCase("MONTH")) {
            interval = Calendar.MONTH;
        } else if (intervalName.equalsIgnoreCase("YEAR")) {
            interval = Calendar.YEAR;
        } else {
            throw new IntervalParseException("invalid interval " + intervalName);
        }
        System.out.println("Set interval to " + interval + " and multiplier to " + intervalMultiplier);
    }

    public void trimOldLogFiles() {
        synchronized (trimOldLogFilesLock) {
            while (oldLogFilesDiskSpaceUsage > maxOldLogfilesDiskUsage) {
                OldLogFile olf;
                // TODO: creates a double lock situation, but only here. I think this is okay because
                //  the inner lock is only used for trivial things.
                synchronized (logFiles) {
                    if (logFiles.isEmpty()) {
                        System.err.println(
                            "ERROR: INCONSISTENT LOGGER TOTALS: Log file list is empty but still used " +
                            oldLogFilesDiskSpaceUsage + " bytes!");
                    }
                    olf = logFiles.removeFirst();
                }
                olf.filename.delete();
                oldLogFilesDiskSpaceUsage -= olf.size;
                if (logMINOR) {
                    Logger.minor(this, "Deleting " + olf.filename + " - saving " + olf.size +
                                       " bytes, disk usage now: " + oldLogFilesDiskSpaceUsage + " of " +
                                       maxOldLogfilesDiskUsage);
                }
            }
        }
    }

    public void start() {
        if (redirectStdOut) {
            try {
                String encName = ENCODING.name();
                System.setOut(
                    new PrintStream(new OutputStreamLogger(LogLevel.NORMAL, "Stdout: ", encName), false,
                                    encName));
                if (redirectStdErr) {
                    System.setErr(
                        new PrintStream(new OutputStreamLogger(LogLevel.ERROR, "Stderr: ", encName),
                                        false, encName));
                }
            } catch (UnsupportedEncodingException e) {
                throw new Error(e);
            }
        }
        wt.setDaemon(true);
        // TODO: SemiOrderedShutdownHook.get().addLateJob(ct);
        wt.start();
    }

    @Override
    public void log(Object o, Class<?> c, String msg, Throwable e, LogLevel priority) {
        if (!instanceShouldLog(priority, c)) {
            return;
        }

        if (closed) {
            return;
        }

        StringBuilder sb = new StringBuilder(e == null ? 512 : 1024);
        int sctr = 0;

        for (int f : fmt) {
            switch (f) {
                case 0:
                    sb.append(str[sctr++]);
                    break;
                case DATE:
                    long now = System.currentTimeMillis();
                    synchronized (this) {
                        myDate.setTime(now);
                        sb.append(df.format(myDate));
                    }
                    break;
                case CLASS:
                    sb.append(c == null ? "<none>" : c.getName());
                    break;
                case HASHCODE:
                    sb.append(o == null ? "<none>" : Integer.toHexString(o.hashCode()));
                    break;
                case THREAD:
                    sb.append(Thread.currentThread().getName());
                    break;
                case PRIORITY:
                    sb.append(priority.name());
                    break;
                case MESSAGE:
                    sb.append(msg);
                    break;
                case UNAME:
                    sb.append(uname);
                    break;
            }
        }
        sb.append('\n');

        // Write stacktrace if available
        for (int j = 0; j < 20 && e != null; j++) {
            sb.append(e);

            StackTraceElement[] trace = e.getStackTrace();

            if (trace == null) {
                sb.append("(null)\n");
            } else if (trace.length == 0) {
                sb.append("(no stack trace)\n");
            } else {
                sb.append('\n');
                for (StackTraceElement elt : trace) {
                    sb.append("\tat ");
                    sb.append(elt.toString());
                    sb.append('\n');
                }
            }

            Throwable cause = e.getCause();
            if (cause != null && cause != e) {
                e = cause;
            } else {
                break;
            }
        }

        try {
            logString(sb.toString().getBytes(ENCODING));
        } catch (UnsupportedEncodingException e1) {
            throw new IllegalStateException(
                "Failed to convert log message to bytes. Unsupported charset encoding: " +
                ENCODING.name(), e1);
        }
    }

    public void logString(byte[] b) throws UnsupportedEncodingException {
        synchronized (list) {
            int sz = list.size();
            if (!list.offer(b)) {
                byte[] ss = list.poll();
                if (ss != null) {
                    listBytes -= ss.length + LINE_OVERHEAD;
                }
                ss = list.poll();
                if (ss != null) {
                    listBytes -= ss.length + LINE_OVERHEAD;
                }
                String err = "GRRR: ERROR: Logging too fast, chopped " + 2 + " entries, " + listBytes +
                             " bytes in memory\n";
                byte[] buf = err.getBytes(ENCODING);
                if (list.offer(buf)) {
                    listBytes += (buf.length + LINE_OVERHEAD);
                }
                if (list.offer(b)) {
                    listBytes += (b.length + LINE_OVERHEAD);
                }
            } else {
                listBytes += (b.length + LINE_OVERHEAD);
            }
            int x = 0;
            if (listBytes > MAX_LIST_BYTES) {
                while ((list.size() > (MAX_LIST_SIZE * 0.9F)) || (listBytes > (MAX_LIST_BYTES * 0.9F))) {
                    byte[] ss;
                    ss = list.poll();
                    listBytes -= (ss.length + LINE_OVERHEAD);
                    x++;
                }
                String err = "GRRR: ERROR: Logging too fast, chopped " + x + " entries, " + listBytes +
                             " bytes in memory\n";
                byte[] buf = err.getBytes(ENCODING);
                if (!list.offer(buf)) {
                    byte[] ss = list.poll();
                    if (ss != null) {
                        listBytes -= ss.length + LINE_OVERHEAD;
                    }
                    if (list.offer(buf)) {
                        listBytes += (buf.length + LINE_OVERHEAD);
                    }
                } else {
                    listBytes += (buf.length + LINE_OVERHEAD);
                }
            }
            if (sz == 0) {
                list.notifyAll();
            }
        }
    }

    public long listBytes() {
        synchronized (list) {
            return listBytes;
        }
    }

    @Override
    public void close() {
        closed = true;
    }

    /**
     * Print a human- and script- readable list of available log files.
     *
     * @throws IOException
     */
    public void listAvailableLogs(OutputStreamWriter writer) throws IOException {
        OldLogFile[] oldLogFiles;
        synchronized (logFiles) {
            oldLogFiles = logFiles.toArray(new OldLogFile[logFiles.size()]);
        }
        DateFormat tempDF =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.ENGLISH);
        tempDF.setTimeZone(TimeZone.getTimeZone("GMT"));
        for (OldLogFile olf : oldLogFiles) {
            writer.write(olf.filename.getName() + " : " + tempDF.format(new Date(olf.start)) + " to " +
                         tempDF.format(new Date(olf.end)) + " - " + olf.size + " bytes\n");
        }
    }

    public void sendLogByContainedDate(long time, OutputStream os) throws IOException {
        OldLogFile toReturn = null;
        synchronized (logFiles) {
            for (OldLogFile olf : logFiles) {
                if (logMINOR) {
                    Logger.minor(this, "Checking " + time + " against " + olf.filename + " : start=" +
                                       olf.start + ", end=" + olf.end);
                }
                if ((time >= olf.start) && (time < olf.end)) {
                    toReturn = olf;
                    if (logMINOR) {
                        Logger.minor(this, "Found " + olf);
                    }
                    break;
                }
            }
            if (toReturn == null) {
                return; // couldn't find it
            }
        }
        FileInputStream fis = new FileInputStream(toReturn.filename);
        DataInputStream dis = new DataInputStream(fis);
        long written = 0;
        long size = toReturn.size;
        byte[] buf = new byte[4096];
        while (written < size) {
            int toRead = (int) Math.min(buf.length, (size - written));
            try {
                dis.readFully(buf, 0, toRead);
            } catch (IOException e) {
                Logger.error(this, "Could not read bytes " + written + " to " + (written + toRead) +
                                   " from file " + toReturn.filename + " which is supposed to be " +
                                   size + " bytes (" + toReturn.filename.length() + ')');
                return;
            }
            os.write(buf, 0, toRead);
            written += toRead;
        }
        dis.close();
        fis.close();
    }

    /**
     * Set the maximum size of old (gzipped) log files to keep. Will start to prune old files
     * immediately, but this will likely not be completed by the time the function returns as it is run
     * off-thread.
     */
    public void setMaxOldLogsSize(long val) {
        synchronized (trimOldLogFilesLock) {
            maxOldLogfilesDiskUsage = val;
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                trimOldLogFiles();
            }
        };
        Thread t = new Thread(r, "Shrink logs");
        t.setDaemon(true);
        t.start();
    }

    public void switchBaseFilename(String filename) {
        synchronized (this) {
            this.baseFilename = filename;
            switchedBaseFilename = true;
        }
    }

    public void waitForSwitch() {
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (!switchedBaseFilename) {
                return;
            }
            long startTime = now;
            long endTime = startTime + 10000;
            while (((now = System.currentTimeMillis()) < endTime) && !switchedBaseFilename) {
                try {
                    wait(Math.max(1, endTime - now));
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }
    }

    public void deleteAllOldLogFiles() {
        synchronized (trimOldLogFilesLock) {
            while (true) {
                OldLogFile olf;
                synchronized (logFiles) {
                    if (logFiles.isEmpty()) {
                        return;
                    }
                    olf = logFiles.removeFirst();
                }
                olf.filename.delete();
                oldLogFilesDiskSpaceUsage -= olf.size;
                if (logMINOR) {
                    Logger.minor(this, "Deleting " + olf.filename + " - saving " + olf.size +
                                       " bytes, disk usage now: " + oldLogFilesDiskSpaceUsage + " of " +
                                       maxOldLogfilesDiskUsage);
                }
            }
        }
    }

    /**
     * This is used by the lost-lock deadlock detector so MUST NOT TAKE A LOCK ever!
     */
    public boolean hasRedirectedStdOutErrNoLock() {
        return redirectStdOut || redirectStdErr;
    }

    public synchronized void setMaxBacklogNotBusy(long val) {
        flushTime = val;
    }

    public Thread getWt() {
        return wt;
    }

    public Thread getCt() {
        return ct;
    }

    static synchronized void getUName() {
        if (!uname.equals("unknown")) {
            return;
        }
        System.out.println("Getting uname for logging");
        try {
            InetAddress addr = InetAddress.getLocalHost();
            if (addr != null) {
                uname = new StringTokenizer(addr.getHostName(), ".").nextToken();
            }
        } catch (Exception e) {
            // Ignored.
        }
    }

    /**
     * The extra parameter int digit is to be used for creating a logfile name when a log exists already
     * with the same date.
     *
     * @param c
     * @param digit      log file name suffix. ignored if this is {@code < 0}
     * @param compressed
     *
     * @return
     */
    protected String getHourLogName(Calendar c, int digit, boolean compressed) {
        StringBuilder buf = new StringBuilder(50);
        buf.append(baseFilename).append('-');
        buf.append(buildNumber);
        buf.append('-');
        buf.append(c.get(Calendar.YEAR)).append('-');
        pad2digits(buf, c.get(Calendar.MONTH) + 1);
        buf.append('-');
        pad2digits(buf, c.get(Calendar.DAY_OF_MONTH));
        buf.append('-');
        pad2digits(buf, c.get(Calendar.HOUR_OF_DAY));
        if (interval == Calendar.MINUTE) {
            buf.append('-');
            pad2digits(buf, c.get(Calendar.MINUTE));
        }
        if (digit > 0) {
            buf.append("-");
            buf.append(digit);
        }
        buf.append(".log");
        if (compressed) {
            buf.append(".gz");
        }
        return buf.toString();
    }

    private StringBuilder pad2digits(StringBuilder buf, int x) {
        String s = Integer.toString(x);
        if (s.length() == 1) {
            buf.append('0');
        }
        buf.append(s);
        return buf;
    }

    private void checkStdStreams(FileLoggerHook this) {
        // Redirect System.err and System.out to the Logger Printstream
        // if they don't exist (like when running under javaw)
        System.out.print(" \b");
        if (System.out.checkError()) {
            redirectStdOut = true;
        }
        System.err.print(" \b");
        if (System.err.checkError()) {
            redirectStdErr = true;
        }
    }

    private void setLogFormat(FileLoggerHook this, String fmt) {
        if ((fmt == null) || fmt.isEmpty()) {
            fmt = "d:c:h:t:p:m";
        }
        char[] f = fmt.toCharArray();

        ArrayList<Integer> fmtVec = new ArrayList<Integer>();
        ArrayList<String> strVec = new ArrayList<String>();

        StringBuilder sb = new StringBuilder();

        boolean comment = false;
        for (char fi : f) {
            int type = numberOf(fi);
            if (type == UNAME) {
                getUName();
            }
            if (!comment && (type != 0)) {
                if (!sb.isEmpty()) {
                    strVec.add(sb.toString());
                    fmtVec.add(0);
                    sb = new StringBuilder();
                }
                fmtVec.add(type);
            } else if (fi == '\\') {
                comment = true;
            } else {
                comment = false;
                sb.append(fi);
            }
        }
        if (!sb.isEmpty()) {
            strVec.add(sb.toString());
            fmtVec.add(0);
        }

        this.fmt = new int[fmtVec.size()];
        int size = fmtVec.size();
        for (int i = 0; i < size; ++i) {
            this.fmt[i] = fmtVec.get(i);
        }

        this.str = new String[strVec.size()];
        str = strVec.toArray(new String[0]);
    }

    private void setDateFormat(FileLoggerHook this, String dfmt) {
        if ((dfmt != null) && !dfmt.isEmpty()) {
            try {
                df = new SimpleDateFormat(dfmt);
            } catch (RuntimeException e) {
                df = DateFormat.getDateTimeInstance();
            }
        } else {
            df = DateFormat.getDateTimeInstance();
        }

        df.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static class OldLogFile {
        public OldLogFile(File currentFilename, long startTime, long endTime, long length) {
            this.filename = currentFilename;
            this.start = startTime;
            this.end = endTime;
            this.size = length;
        }

        final File filename;
        final long start; // inclusive
        final long end; // exclusive
        final long size;
    }

    class WriterThread extends Thread {
        WriterThread(OutputStream logStream) {
            super("Log File Writer Thread");
            this.logStream = logStream;
            if (baseFilename == null) {
                gc = null;
            } else {
                latestFile = new File(baseFilename + "-latest.log");
                previousFile = new File(baseFilename + "-previous.log");
                gc = new GregorianCalendar();
                switch (interval) {
                    case Calendar.YEAR:
                        gc.set(Calendar.MONTH, 0);
                    case Calendar.MONTH:
                        gc.set(Calendar.DAY_OF_MONTH, 0);
                    case Calendar.WEEK_OF_YEAR:
                        if (interval == Calendar.WEEK_OF_YEAR) {
                            gc.set(Calendar.DAY_OF_WEEK, 0);
                        }
                    case Calendar.DAY_OF_MONTH:
                        gc.set(Calendar.HOUR, 0);
                    case Calendar.HOUR:
                        gc.set(Calendar.MINUTE, 0);
                    case Calendar.MINUTE:
                        gc.set(Calendar.SECOND, 0);
                        gc.set(Calendar.MILLISECOND, 0);
                }
                if (intervalMultiplier > 1) {
                    int x = gc.get(interval);
                    gc.set(interval, (x / intervalMultiplier) * intervalMultiplier);
                }
            }
        }

        @Override
        @SuppressWarnings("fallthrough")
        public void run() {
            try {
                byte[] o = null;
                long thisTime;
                long lastTime = -1;
                long startTime;
                long nextHour = -1;
                if (baseFilename != null) {
                    assert previousFile != null : "@AssumeAssertion(nullness)";
                    assert latestFile != null : "@AssumeAssertion(nullness)";
                    assert gc != null : "@AssumeAssertion(nullness)";
                    findOldLogFiles((GregorianCalendar) gc.clone());
                    currentFilename = new File(getHourLogName(gc, -1, true));
                    synchronized (logFiles) {
                        if (!logFiles.isEmpty() && logFiles.getLast().filename.equals(currentFilename)) {
                            logFiles.removeLast();
                        }
                    }
                    openNewStreams(currentFilename, latestFile);
                    System.err.println("Created log files");
                    startTime = gc.getTimeInMillis();
                    if (logMINOR) {
                        Logger.minor(this, "Start time: " + gc + " -> " + startTime);
                    }
                    lastTime = startTime;
                    gc.add(interval, intervalMultiplier);
                    nextHour = gc.getTimeInMillis();
                }
                long timeWaitingForSync = -1;
                while (true) {
                    try {
                        thisTime = System.currentTimeMillis();
                        if (baseFilename != null) {
                            assert gc != null : "@AssumeAssertion(nullness)";

                            if ((thisTime > nextHour) || switchedBaseFilename) {
                                currentFilename = rotateLog(currentFilename, lastTime, nextHour, gc);

                                gc.add(interval, intervalMultiplier);
                                lastTime = nextHour;
                                nextHour = gc.getTimeInMillis();

                                if (switchedBaseFilename) {
                                    synchronized (FileLoggerHook.class) {
                                        switchedBaseFilename = false;
                                    }
                                }
                            }
                        }
                        boolean died = false;
                        boolean timeoutFlush = false;
                        long flush;
                        synchronized (list) {
                            flush = flushTime;
                            long maxWait;
                            if (timeWaitingForSync == -1) {
                                maxWait = Long.MAX_VALUE;
                            } else {
                                maxWait = timeWaitingForSync + flush;
                            }
                            o = list.poll();
                            while (o == null) {
                                if (closed) {
                                    died = true;
                                    break;
                                }
                                try {
                                    if (thisTime < maxWait) {
                                        // Wait no more than 500ms since the CloserThread might be
                                        // waiting
                                        // for closedFinished.
                                        list.wait(Math.min(500L, maxWait - thisTime));
                                        thisTime = System.currentTimeMillis();
                                        if (listBytes < LIST_WRITE_THRESHOLD) {
                                            // Don't write at all until the lower bytes threshold is
                                            // exceeded, or the time threshold is.
                                            assert ((listBytes == 0) == (list.peek() == null));
                                            if (listBytes != 0 && maxWait == Long.MAX_VALUE) {
                                                maxWait = thisTime + flush;
                                            }
                                            if (closed) // If closing, write stuff ASAP.
                                            {
                                                o = list.poll();
                                            } else if (maxWait != Long.MAX_VALUE) {
                                                continue;
                                            }
                                        } else {
                                            // Do NOT use list.poll(timeout) because it uses a
                                            // separate lock.
                                            o = list.poll();
                                        }
                                    }
                                } catch (InterruptedException e) {
                                    // Ignored.
                                }
                                if (o == null) {
                                    if (timeWaitingForSync == -1) {
                                        timeWaitingForSync = thisTime;
                                        maxWait = thisTime + flush;
                                    }
                                    if (thisTime >= maxWait) {
                                        timeoutFlush = true;
                                        timeWaitingForSync =
                                            -1; // We have stuff to write, we are no longer waiting.
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }
                            if (o != null) {
                                listBytes -= o.length + LINE_OVERHEAD;
                            }
                        }
                        assert logStream != null :
                            "@AssumeAssertion(nullness): If baseFilename != null, logStream has been " +
                            "set above; otherwise it has been set in the constructor";
                        if (timeoutFlush || died) {
                            // Flush to disk
                            myWrite(logStream, null);
                            if (altLogStream != null) {
                                myWrite(altLogStream, null);
                            }
                        }
                        if (died) {
                            synchronized (list) {
                                closedFinished = true;
                                list.notifyAll();
                            }
                            return;
                        }
                        if (o == null) {
                            continue;
                        }
                        myWrite(logStream, o);
                        if (altLogStream != null) {
                            myWrite(altLogStream, o);
                        }
                    } catch (OutOfMemoryError e) {
                        System.err.println(e.getClass());
                        System.err.println(e.getMessage());
                        e.printStackTrace();
                        // FIXME
                        //freenet.node.Main.dumpInterestingObjects();
                    } catch (Throwable t) {
                        System.err.println("FileLoggerHook log writer caught " + t);
                        t.printStackTrace(System.err);
                    }
                }
            } finally {
                close();
            }
        }

        /**
         * @param b the bytes to write, null to flush
         */
        protected void myWrite(OutputStream os, byte[] b) {
            long sleepTime = 1000;
            while (true) {
                boolean thrown = false;
                try {
                    if (b != null) {
                        os.write(b);
                    } else {
                        os.flush();
                    }
                } catch (IOException e) {
                    System.err.println("Exception writing to log: " + e + ", sleeping " + sleepTime);
                    thrown = true;
                }
                if (thrown) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                    }
                    sleepTime += sleepTime;
                    if (sleepTime > maxSleepTime) {
                        sleepTime = maxSleepTime;
                    }
                } else {
                    return;
                }
            }
        }

        protected OutputStream openNewLogFile(File filename, boolean compress) {
            while (true) {
                long sleepTime = 1000;
                OutputStream o = null;
                try {
                    o = new FileOutputStream(filename, !logOverwrite);
                    if (compress) {
                        // buffer -> gzip -> buffer -> file
                        o = new BufferedOutputStream(o, 512 * 1024); // to file
                        o = new GZIPOutputStream(o);
                        // gzip block size is 32kB
                        o = new BufferedOutputStream(o, 65536); // to gzipper
                    } else {
                        // buffer -> file
                        o = new BufferedOutputStream(o, 512 * 1024);
                    }
                    o.write(BOM);
                    return o;
                } catch (IOException e) {
                    if (o != null) {
                        try {
                            o.close();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    System.err.println("Could not create FOS " + filename + ": " + e);
                    System.err.println("Sleeping " + sleepTime / 1000 + " seconds");
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ex) {
                    }
                    sleepTime += sleepTime;
                }
            }
        }

        /**
         * Initialize oldLogFiles
         */
        private void findOldLogFiles(GregorianCalendar gc) {
            gc = (GregorianCalendar) gc.clone();
            File currentFilename = new File(getHourLogName(gc, -1, true));
            System.out.println("Finding old log files. New log file is " + currentFilename);
            File numericSameDateFilename;
            int slashIndex = baseFilename.lastIndexOf(File.separatorChar);
            File dir;
            String prefix;
            if (slashIndex == -1) {
                dir = new File(System.getProperty("user.dir"));
                prefix = baseFilename.toLowerCase();
            } else {
                dir = new File(baseFilename.substring(0, slashIndex));
                prefix = baseFilename.substring(slashIndex + 1).toLowerCase();
            }
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            Arrays.sort(files);
            long lastStartTime = -1;
            File oldFile = null;
            if (latestFile.exists()) {
                FileUtil.renameTo(latestFile, previousFile);
            }

            for (File f : files) {
                String name = f.getName();
                if (name.toLowerCase().startsWith(prefix)) {
                    if (name.equals(previousFile.getName()) || name.equals(latestFile.getName())) {
                        continue;
                    }
                    if (!name.endsWith(".log.gz")) {
                        if (logMINOR) {
                            Logger.minor(this, "Does not end in .log.gz: " + name);
                        }
                        f.delete();
                        continue;
                    } else {
                        name = name.substring(0, name.length() - ".log.gz".length());
                    }
                    name = name.substring(prefix.length());
                    if (name.isEmpty() || (name.charAt(0) != '-')) {
                        if (logMINOR) {
                            Logger.minor(this,
                                         "Deleting unrecognized: " + name + " (" + f.getPath() + ')');
                        }
                        f.delete();
                        continue;
                    } else {
                        name = name.substring(1);
                    }
                    String[] tokens = name.split("-");
                    int[] nums = new int[tokens.length];
                    for (int j = 0; j < tokens.length; j++) {
                        try {
                            nums[j] = Integer.parseInt(tokens[j]);
                        } catch (NumberFormatException e) {
                            Logger.normal(this,
                                          "Could not parse: " + tokens[j] + " into number from " + name);
                            // Broken
                            f.delete();
                            continue;
                        }
                    }
                    if (nums.length > 1) {
                        gc.set(Calendar.YEAR, nums[1]);
                    }
                    if (nums.length > 2) {
                        gc.set(Calendar.MONTH, nums[2] - 1);
                    }
                    if (nums.length > 3) {
                        gc.set(Calendar.DAY_OF_MONTH, nums[3]);
                    }
                    if (nums.length > 4) {
                        gc.set(Calendar.HOUR_OF_DAY, nums[4]);
                    }
                    if (nums.length > 5) {
                        gc.set(Calendar.MINUTE, nums[5]);
                    }
                    gc.set(Calendar.SECOND, 0);
                    gc.set(Calendar.MILLISECOND, 0);
                    long startTime = gc.getTimeInMillis();
                    if (oldFile != null) {
                        long l = oldFile.length();
                        OldLogFile olf = new OldLogFile(oldFile, lastStartTime, startTime, l);
                        synchronized (logFiles) {
                            logFiles.addLast(olf);
                        }
                        synchronized (trimOldLogFilesLock) {
                            oldLogFilesDiskSpaceUsage += l;
                        }
                    }
                    lastStartTime = startTime;
                    oldFile = f;
                } else {
                    // Nothing to do with us
                    Logger.normal(this, "Unknown file: " + name + " in the log directory");
                }
            }
            //If a compressed log file already exists for a given date,
            //add a number to the end of the file that already exists
            if (currentFilename != null && currentFilename.exists()) {
                System.out.println("Old log file exists for this time period: " + currentFilename);
                for (int a = 1; ; a++) {
                    numericSameDateFilename = new File(getHourLogName(gc, a, true));
                    if (numericSameDateFilename == null || !numericSameDateFilename.exists()) {
                        if (numericSameDateFilename != null) {
                            System.out.println("Renaming to: " + numericSameDateFilename);
                            FileUtil.renameTo(currentFilename, numericSameDateFilename);
                        }
                        break;
                    }
                }
            }
            if (oldFile != null) {
                long l = oldFile.length();
                OldLogFile olf = new OldLogFile(oldFile, lastStartTime, System.currentTimeMillis(), l);
                synchronized (logFiles) {
                    logFiles.addLast(olf);
                }
                synchronized (trimOldLogFilesLock) {
                    oldLogFilesDiskSpaceUsage += l;
                }
            }
            trimOldLogFiles();
        }

        private void openNewStreams(File fileName, File altFilename) {
            close();

            logStream = openNewLogFile(fileName, true);
            if (altFilename != null) {
                altLogStream = openNewLogFile(altFilename, false);
            }
        }

        private void close() {
            if (logStream != null) {
                try {
                    logStream.flush();
                    logStream.close();
                } catch (IOException e) {
                    System.err.println("Closing on change caught " + e);
                }
            }

            if (altLogStream != null) {
                try {
                    altLogStream.flush();
                    altLogStream.close();
                } catch (IOException e) {
                    System.err.println("Failed to close compressed log stream: " + e);
                }
            }
        }

        private File rotateLog(
            File currentFilename, long lastTime, long nextHour,
            GregorianCalendar gc) {

            if (currentFilename != null) {
                long length = currentFilename.length();
                OldLogFile olf = new OldLogFile(currentFilename, lastTime, nextHour, length);
                synchronized (logFiles) {
                    logFiles.addLast(olf);
                }
                oldLogFilesDiskSpaceUsage += length;
                trimOldLogFiles();
            }
            // Rotate primary log stream
            currentFilename = new File(getHourLogName(gc, -1, true));
            if (latestFile != null) {
                if (previousFile != null && latestFile.exists()) {
                    FileUtil.renameTo(latestFile, previousFile);
                }
                if (!latestFile.delete()) {
                    System.err.println("Failed to delete " + latestFile);
                }
            }
            openNewStreams(currentFilename, latestFile);

            return currentFilename;
        }

        // Check every minute
        static final int maxSleepTime = 60 * 1000;

        private final GregorianCalendar gc;
        /**
         * Other stream to write data to (may be null)
         */
        protected OutputStream altLogStream;
        protected File latestFile;
        protected File previousFile;
        /**
         * Stream to write data to (compressed if rotate is on)
         */
        private OutputStream logStream;
        private File currentFilename;

    }

    class CloserThread extends Thread {
        @Override
        public void run() {
            synchronized (list) {
                closed = true;
                long deadline = System.currentTimeMillis() + SECONDS.toMillis(10);
                while (!closedFinished) {
                    int wait = (int) (deadline - System.currentTimeMillis());
                    if (wait <= 0) {
                        return;
                    }
                    try {
                        list.wait(wait);
                    } catch (InterruptedException e) {
                        // Ok.
                    }
                }
                System.out.println("Completed writing logs to disk.");
            }
        }
    }

    private static final Charset ENCODING = StandardCharsets.UTF_8;
    private static final byte[] BOM = "\uFEFF".getBytes(ENCODING);
    /**
     * Memory allocation overhead (estimated through experimentation with bsh)
     */
    private static final int LINE_OVERHEAD = 60;
    private static volatile boolean logMINOR;
    /**
     * Name of the local host (called uname in Unix-like operating systems).
     */
    private static String uname;
    protected final boolean logOverwrite;
    protected final int MAX_LIST_SIZE;
    /**
     * Something weird happens when the disk gets full, also we don't want to block So run the actual
     * write on another thread
     * <p>
     * Unfortunately, we can't use ConcurrentBlockingQueue because we need to dump stuff when the queue
     * gets too big.
     * <p>
     * FIXME PERFORMANCE: Using an ArrayBlockingQueue avoids some unnecessary memory allocations, but it
     * means we have to take two locks.
     * Seriously consider reverting 88268b99856919df0d42c2787d9ea3674a9f6f0d.
     * .e359b4005ef728a159fdee988c483de8ce8f3f6b
     * to go back to one lock and a LinkedList.
     */
    protected final ArrayBlockingQueue<byte[]> list;
    protected final Deque<OldLogFile> logFiles = new ArrayDeque<OldLogFile>();
    private final Object trimOldLogFilesLock = new Object();
    private final Date myDate = new Date();
    private final int buildNumber;
    private final WriterThread wt;
    private final CloserThread ct = new CloserThread();
    protected int interval = Calendar.MINUTE;
    protected int intervalMultiplier = 5;
    /* Base filename for rotating logs */
    protected String baseFilename = null;
    /* Whether to redirect stdout */
    protected boolean redirectStdOut = false;
    /* Whether to redirect stderr */
    protected boolean redirectStdErr = false;
    protected long MAX_LIST_BYTES = 10 * (1 << 20);
    protected long LIST_WRITE_THRESHOLD;
    protected long listBytes = 0;
    protected int runningCompressors = 0;
    protected Object runningCompressorsSync = new Object();
    long maxOldLogfilesDiskUsage;
    private volatile boolean closed = false;
    private boolean closedFinished = false;
    private DateFormat df;
    private int[] fmt;
    private String[] str;
    private long oldLogFilesDiskSpaceUsage = 0;
    // Unless we are writing flat out, everything will hit disk within this period.
    private long flushTime = 1000; // Default is 1 second. Will be set by setMaxBacklogNotBusy().
    private boolean switchedBaseFilename;
}
