/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api;

/** Unified logging interface for LibreLogin supporting multiple severity levels. */
public interface Logger {

    /**
     * Logs a message at the INFO level.
     *
     * @param message the message to log
     */
    void info(String message);

    /**
     * Logs a message along with an associated throwable at the INFO level.
     *
     * @param message the message to log
     * @param throwable the throwable to log
     */
    void info(String message, Throwable throwable);

    /**
     * Logs a formatted message at the INFO level.
     *
     * @param format the format string (SLF4J-style {})
     * @param arguments the arguments
     */
    default void info(String format, Object... arguments) {
        var cause = trailingThrowable(arguments);
        if (cause != null) {
            info(formatPlaceholders(format, arguments), cause);
        } else {
            info(formatPlaceholders(format, arguments));
        }
    }

    /**
     * Logs a message at the WARNING level.
     *
     * @param message the message to log
     */
    void warn(String message);

    /**
     * Logs a message along with an associated throwable at the WARNING level.
     *
     * @param message the message to log
     * @param throwable the throwable to log
     */
    void warn(String message, Throwable throwable);

    /**
     * Logs a formatted message at the WARNING level.
     *
     * @param format the format string (SLF4J-style {})
     * @param arguments the arguments
     */
    default void warn(String format, Object... arguments) {
        var cause = trailingThrowable(arguments);
        if (cause != null) {
            warn(formatPlaceholders(format, arguments), cause);
        } else {
            warn(formatPlaceholders(format, arguments));
        }
    }

    /**
     * Logs a message at the ERROR level.
     *
     * @param message the message to log
     */
    void error(String message);

    /**
     * Logs a message along with an associated throwable at the ERROR level.
     *
     * @param message the message to log
     * @param throwable the throwable to log
     */
    void error(String message, Throwable throwable);

    /**
     * Logs a formatted message at the ERROR level.
     *
     * @param format the format string (SLF4J-style {})
     * @param arguments the arguments
     */
    default void error(String format, Object... arguments) {
        var cause = trailingThrowable(arguments);
        if (cause != null) {
            error(formatPlaceholders(format, arguments), cause);
        } else {
            error(formatPlaceholders(format, arguments));
        }
    }

    /**
     * Logs a message at the DEBUG level.
     *
     * @param message the message to log
     */
    void debug(String message);

    /**
     * Logs a message along with an associated throwable at the DEBUG level.
     *
     * @param message the message to log
     * @param throwable the throwable to log
     */
    void debug(String message, Throwable throwable);

    /**
     * Logs a formatted message at the DEBUG level.
     *
     * @param format the format string (SLF4J-style {})
     * @param arguments the arguments
     */
    default void debug(String format, Object... arguments) {
        if (!isDebugEnabled()) {
            return;
        }
        var cause = trailingThrowable(arguments);
        if (cause != null) {
            debug(formatPlaceholders(format, arguments), cause);
        } else {
            debug(formatPlaceholders(format, arguments));
        }
    }

    /**
     * Checks if debug logging is enabled.
     *
     * @return {@code true} if debug logging is enabled, {@code false} otherwise
     */
    default boolean isDebugEnabled() {
        return false;
    }

    /**
     * Replaces SLF4J-style {@code {}} placeholders with the arguments, in order. A trailing {@link
     * Throwable} argument is not substituted (it is logged as the cause).
     *
     * @param format the message pattern
     * @param arguments the arguments
     * @return the formatted message
     */
    static String formatPlaceholders(String format, Object... arguments) {
        if (format == null || arguments == null || arguments.length == 0) {
            return format;
        }
        int usable = trailingThrowable(arguments) != null ? arguments.length - 1 : arguments.length;
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

    private static Throwable trailingThrowable(Object[] arguments) {
        return arguments != null
                        && arguments.length > 0
                        && arguments[arguments.length - 1] instanceof Throwable t
                ? t
                : null;
    }
}
