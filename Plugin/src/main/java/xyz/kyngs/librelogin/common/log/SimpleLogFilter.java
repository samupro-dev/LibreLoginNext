/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.log;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * {@link java.util.logging.Logger} implementation of {@link LogFilter} that wraps existing JUL
 * filters to prevent sensitive authentication commands and credentials from being written to logs.
 */
public class SimpleLogFilter extends LogFilter implements Filter {

    private final Filter filter;
    private final Logger logger;

    /**
     * Constructs a new simple log filter preserving any existing logger filter in chain.
     *
     * @param logger the java.util.logging logger instance to wrap
     */
    public SimpleLogFilter(Logger logger) {
        filter = logger.getFilter();
        this.logger = logger;
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        if (filter != null && !filter.isLoggable(record)) return false;

        return checkMessage(record.getMessage());
    }

    @Override
    public void inject() {
        logger.setFilter(this);
    }

    @Override
    public void remove() {
        if (logger.getFilter() == this) {
            logger.setFilter(filter);
        }
    }
}
