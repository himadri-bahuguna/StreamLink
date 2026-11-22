package com.streamlink.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Minimal timestamped logger that also prints the worker thread name. */
public final class Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() {
    }

    public static void info(String message) {
        System.out.println(prefix() + message);
    }

    public static void warn(String message) {
        System.err.println(prefix() + "WARN " + message);
    }

    public static void error(String message, Throwable error) {
        System.err.println(prefix() + "ERROR " + message
                + (error == null ? "" : " (" + error.getClass().getSimpleName()
                        + ": " + error.getMessage() + ")"));
    }

    private static String prefix() {
        return "[" + LocalTime.now().format(TIME) + "][" + Thread.currentThread().getName() + "] ";
    }
}
