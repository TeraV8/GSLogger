/*
 * Geneva Sky Logger
 * This software is owned and provided as-is by TeraV.
 * Any usage of this code is subject to the GNU General Public License v3.0
 *
 * Any concerns about this software should be raised at its GitHub Repository, https://github.com/TeraV8/GSLogger
*/

package io.terav.geneva;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    protected LinkedStream[] channels;
    private final PrintStream infoStream;
    private final PrintStream errorStream;
    private final PrintStream traceStream;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd:HHmmss");
    protected Throwable lastError;
    public Level maxPrintable = Level.INFO;
    private Thread flusher;
    /**
     * Creates a new <code>Logger</code> with 4 unlinked channels.
     */
    public Logger() {
        this(4);
    }
    /**
     * Creates a new <code>Logger</code> with the specified number of channels.
     * @param channels The number of unlinked channels to create
     */
    public Logger(int channels) {
        this.channels = new LinkedStream[channels];
        for (int i=0;i<channels;i++) {
            this.channels[i] = new LinkedStream();
        }
        infoStream = new PrintStream(new LogStream(Level.INFO));
        errorStream = new PrintStream(new LogStream(Level.ERROR));
        traceStream = new PrintStream(new LogStream(Level.STACKTRACE));
        flusher = new Thread(new LogFlusher());
        flusher.setName("Logger-Flush");
        flusher.setDaemon(true);
        flusher.start();
    }
    /**
     * Binds a stream that will be written to when a message is sent with greater than or equal to importance of <code>INFO</code>.
     * @param out The stream to write to
     * @see linkStream(OutputStream, Level)
     */
    public void linkStream(OutputStream out) {
        linkStream(out, Level.INFO);
    }
    /**
     * Binds a stream that will be written to when a message is sent with greater than or equal to importance as <code>maxLogLevel</code>.
     * @param out The stream to write to
     * @param maxLogLevel The minimum importance level for messages to be written
     * @see unlinkStream(OutputStream)
     */
    public void linkStream(OutputStream out, Level maxLogLevel) {
        if (out == null)
            throw new NullPointerException("Cannot link null stream");
        if (maxLogLevel == null)
            throw new NullPointerException("Cannot use null log level");
        for (LinkedStream channel : channels) {
            if (channel.stream == out) return; // has already been linked
            if (channel.stream != null) continue;
            channel.stream = new BufferedOutputStream(out);
            channel.maxLevel = maxLogLevel;
            return;
        }
    }
    /**
     * Unbinds a previously bound stream
     * @param out The stream to unbind
     * @see Logger#linkStream(OutputStream, Level) 
     */
    public void unlinkStream(OutputStream out) {
        if (out == null)
            throw new NullPointerException("Cannot index null stream");
        for (LinkedStream channel : channels) {
            if (channel.stream == out)
                channel.stream = null;
        }
    }
    /**
     * Returns a stream to which any string written will be sent as an <code>INFO</code> message.
     * @return 
     */
    public PrintStream getInfoStream() {
        return infoStream;
    }
    /**
     * Returns a stream to which any string written will be sent as an <code>ERROR</code> message.
     * @return
     */
    public PrintStream getErrorStream() {
        return errorStream;
    }
    /**
     * Returns the last error thrown by the <code>Logger</code>.
     * Errors encountered by the <code>Logger</code> are not immediately thrown, but saved to be read by <code>pullError()</code>.
     * @return The last generated error
     */
    public Throwable pullError() {
        Throwable t = lastError;
        lastError = null;
        return t;
    }
    /**
     * Logs a message.
     * Log messages are formatted as:
     * <pre>
     * [timestamp|thread-name] &lt;LEVEL&gt; message
     * </pre>
     * For every element of <code>params</code>, the object is stringified and replaces "{" <code>index</code> "}", where <code>index</code> is the element's index in the <code>params</code> array.
     * 
     * @param logLevel The importance level of the message.
     * @param msg The message template to use.
     * @param params The objects to stringify for insertion in the message.
     */
    public void log(Level logLevel, String msg, Object... params) {
        LocalDateTime timestamp = LocalDateTime.now();
        formatter.format(timestamp);
        String finalMsg = "[" + formatter.format(timestamp);
        finalMsg += '|' + Thread.currentThread().getName() + "] <" + logLevel + "> ";
        for (int i=0;i<params.length;i++) {
            msg = msg.replace("{" + i + "}", params[i].toString());
        }
        msg = msg.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "    ").replace("\b", "\\b").replace("\033", "\\e");
        finalMsg += msg + '\n';
        for (LinkedStream channel : channels) {
            if (channel.maxLevel.ordinal() > logLevel.ordinal()) continue;
            try {
                channel.write(finalMsg.getBytes());
            } catch (IOException ioe) {
                lastError = ioe;
            }
        }
    }
    /**
     * Logs a message with the stacktrace of the <code>Throwable</code>.
     * @param t The <code>Throwable</code> to log
     * @param level The importance level of the initial message
     * @see Logger#log(Logger.Level, String, Object...)
     */
    public void log(Throwable t, Level level) {
        log(level, t.toString());
        t.printStackTrace(traceStream);
    }
    /**
     * Sends a log message with the <code>Logger.Level.DEBUG</code> level.
     * @param msg 
     */
    public void debug(String msg) {
        log(Level.DEBUG, msg);
    }
    /**
     * Sends a log message with the <code>Logger.Level.INFO</code> level.
     * @param msg 
     */
    public void info(String msg) {
        log(Level.INFO, msg);
    }
    /**
     * Sends a log message with the <code>Logger.Level.WARNING</code> level.
     * @param msg 
     */
    public void warn(String msg) {
        log(Level.WARNING, msg);
    }
    /**
     * Sends a log message with the <code>Logger.Level.ERROR</code> level.
     * @param msg 
     */
    public void error(String msg) {
        log(Level.ERROR, msg);
    }
    /**
     * Enum indicating the importance of a log message.
     */
    public static enum Level {
        /**
         * Used for <code>Throwable</code> stacktraces.
         */
        STACKTRACE,
        /**
         * Used to indicate debug tracing information.
         */
        TRACE,
        /**
         * Used to indicate general debug information.
         */
        DEBUG,
        /**
         * Used to indicate informative messages.
         */
        INFO,
        /**
         * Used to indicate warnings.
         */
        WARNING,
        /**
         * Used to indicate errors.
         */
        ERROR,
        /**
         * Used to indicate fatal errors, usually crashing the application.
         */
        FATAL
    }
    protected class LinkedStream extends OutputStream {
        private BufferedOutputStream stream = null;
        public Level maxLevel = Level.INFO;
        private boolean flush = false;
        @Override
        public void write(byte[] b) throws IOException {
            if (stream == null) return;
            synchronized (stream) {
                for (byte i : b)
                    stream.write((byte) i);
            }
            flush = true;
            flusher.interrupt();
        }
        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte)b});
        }
    }
    private class LogStream extends OutputStream {
        public final Level logLevel;
        private String line = null;
        private LogStream(Level l) {
            logLevel = l;
        }
        @Override
        public synchronized void write(int b) throws IOException {
            if (b == '\n') {
                flushLine();
                return;
            }
            if (line == null)
                line = Character.toString((char)b);
            else
                line += (char) b;
            if (line.length() == 1023) flushLine();
        }
        private void flushLine() throws IOException {
            if (line == null) return;
            log(logLevel, line);
            line = null;
        }
    }
    private class LogFlusher implements Runnable {
        @Override
        public void run() {
            Runtime.getRuntime().addShutdownHook(new Thread(this::forceFlush));
            while (true) {
                try { Thread.sleep(3000); } catch (InterruptedException e) {}
                for (LinkedStream s : channels)
                    if (s.flush) {
                        s.flush = false;
                        try {
                            synchronized (s.stream) { s.stream.flush(); }
                        } catch (IOException e) {}
                    }
            }
        }
        private void forceFlush() {
            for (LinkedStream s : channels)
                if (s.flush) {
                    s.flush = false;
                    try {
                        s.stream.flush();
                    } catch (IOException ioe) {}
                }
        }
    }
}
