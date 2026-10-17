/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common;

import java.util.function.Supplier;
import java.util.logging.Level;
import xyz.kyngs.librelogin.api.Logger;

/**
 * {@link Logger} backed by {@code java.util.logging}, for platforms that do not expose an SLF4J
 * logger to plugins (Paper before 1.17). Supports SLF4J-style {@code {}} placeholders, with a
 * trailing {@link Throwable} argument logged as the cause.
 */
public class JULLogger implements Logger {

    private final java.util.logging.Logger jul;
    private final Supplier<Boolean> debug;

    /**
     * Constructs a new {@link JULLogger}.
     *
     * @param jul the underlying logger
     * @param debug supplier telling whether debug output is enabled
     */
    public JULLogger(java.util.logging.Logger jul, Supplier<Boolean> debug) {
        this.jul = jul;
        this.debug = debug;
    }

    @Override
    public void info(String message) {
        jul.log(Level.INFO, message);
    }

    @Override
    public void info(String message, Throwable throwable) {
        jul.log(Level.INFO, message, throwable);
    }

    @Override
    public void info(String format, Object... arguments) {
        log(Level.INFO, format, arguments);
    }

    @Override
    public void warn(String message) {
        jul.log(Level.WARNING, message);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        jul.log(Level.WARNING, message, throwable);
    }

    @Override
    public void warn(String format, Object... arguments) {
        log(Level.WARNING, format, arguments);
    }

    @Override
    public void error(String message) {
        jul.log(Level.SEVERE, message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        jul.log(Level.SEVERE, message, throwable);
    }

    @Override
    public void error(String format, Object... arguments) {
        log(Level.SEVERE, format, arguments);
    }

    @Override
    public boolean isDebugEnabled() {
        return Boolean.TRUE.equals(debug.get());
    }

    @Override
    public void debug(String message) {
        if (isDebugEnabled()) {
            jul.log(Level.INFO, "[DEBUG] " + message);
        }
    }

    @Override
    public void debug(String message, Throwable throwable) {
        if (isDebugEnabled()) {
            jul.log(Level.INFO, "[DEBUG] " + message, throwable);
        }
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (isDebugEnabled()) {
            log(Level.INFO, "[DEBUG] " + format, arguments);
        }
    }

    private void log(Level level, String format, Object[] arguments) {
        if (!jul.isLoggable(level)) {
            return;
        }
        Throwable cause = null;
        int usable = arguments == null ? 0 : arguments.length;
        if (usable > 0 && arguments[usable - 1] instanceof Throwable t) {
            cause = t;
            usable--;
        }
        jul.log(level, format(format, arguments, usable), cause);
    }

    static String format(String format, Object[] arguments, int usable) {
        if (format == null || usable == 0) {
            return format;
        }
        var out = new StringBuilder(format.length() + 16 * usable);
        int from = 0;
        int arg = 0;
        int at;
        while (arg < usable && (at = format.indexOf("{}", from)) >= 0) {
            out.append(format, from, at).append(arguments[arg++]);
            from = at + 2;
        }
        return out.append(format, from, format.length()).toString();
    }
}
