/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.mail;

/** Service interface for sending transactional emails. */
public interface EmailHandler {

    /**
     * Sends an email with arbitrary content to the specified recipient.
     *
     * @param email the recipient email address
     * @param subject the email subject line
     * @param content the body content of the email
     */
    void sendEmail(String email, String subject, String content);

    /**
     * Sends a test connectivity email to the specified recipient.
     *
     * @param email the recipient email address
     */
    void sendTestMail(String email);

    /**
     * Sends a password reset notification email containing a security token.
     *
     * @param email the recipient email address
     * @param token the security token for password reset verification
     * @param username the username of the account requesting the reset
     * @param ip the remote IP address that requested the reset
     */
    void sendPasswordResetMail(String email, String token, String username, String ip);

    /**
     * Sends an email address verification request containing an activation token.
     *
     * @param email the recipient email address to verify
     * @param token the email confirmation token
     * @param username the username of the account being linked
     */
    void sendVerificationMail(String email, String token, String username);
}
