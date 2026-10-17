/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.premium;

/** Thrown when an error occurs during premium account lookup or Mojang API queries. */
public class PremiumException extends Exception {

    /** The classified cause category of this exception. */
    private final Issue issue;

    /**
     * Constructs a new PremiumException with the specified cause category and underlying exception.
     *
     * @param issue the category of issue that caused the failure
     * @param exception the underlying cause exception
     */
    public PremiumException(Issue issue, Exception exception) {
        super(exception);
        this.issue = issue;
    }

    /**
     * Constructs a new PremiumException with the specified cause category and detail message.
     *
     * @param issue the category of issue that caused the failure
     * @param message the detail explanation message
     */
    public PremiumException(Issue issue, String message) {
        super(message);
        this.issue = issue;
    }

    /**
     * Gets the classified issue category for this exception.
     *
     * @return the issue category
     */
    public Issue getIssue() {
        return issue;
    }

    /** Categories of failure encountered during premium account operations. */
    public enum Issue {
        /** The upstream API rate limit or throttle was exceeded. */
        THROTTLED,
        /** The upstream API responded with an error or invalid payload. */
        SERVER_EXCEPTION,
        /** An unclassified or unexpected error occurred. */
        UNDEFINED
    }
}
