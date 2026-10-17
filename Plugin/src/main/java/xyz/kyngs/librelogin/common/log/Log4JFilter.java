/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.message.Message;

/**
 * Log4j2 implementation of {@link LogFilter} that intercepts console and file logging events to
 * suppress sensitive credential-bearing commands such as passwords and 2FA tokens.
 */
public class Log4JFilter extends LogFilter implements Filter {

    @Override
    public void inject() {
        ((Logger) LogManager.getRootLogger()).addFilter(this);
    }

    @Override
    public void remove() {
        ((Logger) LogManager.getRootLogger()).get().removeFilter(this);
    }

    /**
     * Evaluates a message string and its parameters, converting the boolean check into a Log4j2
     * {@link Result}.
     *
     * @param message the log message text or pattern
     * @param params optional parameters to format into the message
     * @return {@link Result#NEUTRAL} if allowed, or {@link Result#DENY} if suppressed
     */
    private Result checkMessageResult(String message, Object... params) {
        if (message == null) {
            return Result.NEUTRAL;
        }
        String formatted =
                (params != null && params.length > 0)
                        ? org.apache.logging.log4j.message.ParameterizedMessage.format(
                                message, params)
                        : message;
        return checkMessage(formatted) ? Result.NEUTRAL : Result.DENY;
    }

    @Override
    public Result getOnMatch() {
        return Result.NEUTRAL;
    }

    @Override
    public Result getOnMismatch() {
        return Result.NEUTRAL;
    }

    @Override
    public Result filter(
            Logger logger, Level level, Marker marker, String message, Object... params) {
        return checkMessageResult(message, params);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String message, Object p0) {
        return checkMessageResult(message, p0);
    }

    @Override
    public Result filter(
            Logger logger, Level level, Marker marker, String message, Object p0, Object p1) {
        return checkMessageResult(message, p0, p1);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2) {
        return checkMessageResult(message, p0, p1, p2);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3) {
        return checkMessageResult(message, p0, p1, p2, p3);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4) {
        return checkMessageResult(message, p0, p1, p2, p3, p4);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5) {
        return checkMessageResult(message, p0, p1, p2, p3, p4, p5);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6) {
        return checkMessageResult(message, p0, p1, p2, p3, p4, p5, p6);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6,
            Object p7) {
        return checkMessageResult(message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6,
            Object p7,
            Object p8) {
        return checkMessageResult(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    @Override
    public Result filter(
            Logger logger,
            Level level,
            Marker marker,
            String message,
            Object p0,
            Object p1,
            Object p2,
            Object p3,
            Object p4,
            Object p5,
            Object p6,
            Object p7,
            Object p8,
            Object p9) {
        return checkMessageResult(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return checkMessageResult(msg != null ? msg.toString() : null);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return checkMessageResult(msg != null ? msg.getFormattedMessage() : null);
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null || event.getMessage() == null) {
            return Result.NEUTRAL;
        }
        return checkMessageResult(event.getMessage().getFormattedMessage());
    }

    @Override
    public State getState() {
        return State.STARTED;
    }

    @Override
    public void initialize() {}

    @Override
    public boolean isStarted() {
        return true;
    }

    @Override
    public boolean isStopped() {
        return false;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}
}
