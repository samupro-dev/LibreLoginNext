/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

/**
 * Basic rate limiter using Caffeine's cache as a backend.<br>
 * <b>This implementation is thread-safe.</b>
 *
 * @param <T> key type
 * @author kyngs
 */
public class RateLimiter<T> {

    private static final Object PRESENT = new Object();
    private final Cache<T, Object> expiring;

    /**
     * Constructs a new rate limiter with the specified expiration time.
     *
     * @param amount the duration amount
     * @param unit the time unit of the duration
     */
    public RateLimiter(long amount, TimeUnit unit) {
        expiring = Caffeine.newBuilder().expireAfterWrite(amount, unit).maximumSize(50_000).build();
    }

    /**
     * Attempts to process an operation for the given key and determines if it should be
     * rate-limited.
     *
     * @param t the key to check
     * @return {@code true} if the operation is rate-limited (too frequent), {@code false} if it is
     *     permitted
     */
    public boolean tryAndLimit(T t) {
        return expiring.asMap().putIfAbsent(t, PRESENT) != null;
    }
}
