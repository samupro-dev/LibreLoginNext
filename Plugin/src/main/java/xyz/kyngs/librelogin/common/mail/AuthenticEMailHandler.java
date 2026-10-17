/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.mail;

import java.time.Duration;
import java.util.Objects;
import org.apache.commons.mail.EmailConstants;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import xyz.kyngs.librelogin.api.mail.EmailHandler;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.util.GeneralUtil;

/**
 * Default implementation of {@link EmailHandler} using Apache Commons Email over SMTP.
 *
 * <p>Supports automated TLS/SSL protocol negotiation based on the configured SMTP port:
 *
 * <ul>
 *   <li><b>Port 465:</b> SMTPS with implicit SSL on connect and strict certificate identity
 *       verification.
 *   <li><b>Port 587:</b> SMTP with explicit STARTTLS requirement and strict certificate identity
 *       verification.
 *   <li><b>Other ports:</b> Standard plaintext SMTP without transport encryption.
 * </ul>
 *
 * Also manages thread context classloader switching during transmission to prevent JavaMail service
 * provider lookup failures.
 *
 * <p>This class is thread-safe.
 */
public class AuthenticEMailHandler implements EmailHandler {

    private final AuthenticLibreLogin<?, ?> plugin;

    /**
     * Constructs a new {@code AuthenticEMailHandler} with the given plugin instance.
     *
     * @param plugin the plugin instance providing configuration and message templates
     */
    public AuthenticEMailHandler(AuthenticLibreLogin<?, ?> plugin) {
        this.plugin = plugin;
    }

    @Override
    public void sendEmail(String email, String subject, String content) {
        plugin.reportMainThread();
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(content, "content must not be null");

        try {
            var config = plugin.getConfiguration();
            var port = config.get(ConfigurationKeys.MAIL_PORT);

            var mail = new HtmlEmail();

            mail.setCharset(EmailConstants.UTF_8);
            mail.setHostName(config.get(ConfigurationKeys.MAIL_HOST));
            mail.setSmtpPort(port);
            mail.setSubject(subject);
            mail.setAuthentication(
                    config.get(ConfigurationKeys.MAIL_USERNAME),
                    config.get(ConfigurationKeys.MAIL_PASSWORD));
            mail.addTo(email);
            mail.setFrom(
                    config.get(ConfigurationKeys.MAIL_EMAIL),
                    config.get(ConfigurationKeys.MAIL_SENDER));

            mail.setSocketConnectionTimeout(Duration.ofSeconds(5));
            mail.setSocketTimeout(Duration.ofSeconds(5));

            switch (port) {
                case 465 -> {
                    mail.setSslSmtpPort(String.valueOf(port));
                    mail.setSSLOnConnect(true);
                    mail.setSSLCheckServerIdentity(true);
                }
                case 587 -> {
                    mail.setStartTLSEnabled(true);
                    mail.setStartTLSRequired(true);
                    mail.setSSLCheckServerIdentity(true);
                }
                default -> {
                    mail.setStartTLSEnabled(false);
                    mail.setSSLOnConnect(false);
                }
            }

            mail.setHtmlMsg(content);
            var previousClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                mail.send();
            } finally {
                Thread.currentThread().setContextClassLoader(previousClassLoader);
            }
        } catch (EmailException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendTestMail(String email) {
        sendEmail(
                email,
                java.util.Objects.requireNonNullElse(
                        plugin.getMessages().getRawMessage("email.test-subject"),
                        "LibreLogin Next test mail"),
                java.util.Objects.requireNonNullElse(
                        plugin.getMessages().getRawMessage("email.test-content"),
                        "LibreLogin Next can send emails."));
    }

    @Override
    public void sendPasswordResetMail(String email, String token, String username, String ip) {
        String senderName =
                java.util.Objects.requireNonNullElse(
                        plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER), "LibreLogin");
        String rawSubject =
                java.util.Objects.requireNonNullElse(
                        plugin.getMessages().getRawMessage("email.password-reset-subject"),
                        "Reset your password on %server%");
        String rawContent =
                java.util.Objects.requireNonNullElse(
                        plugin.getMessages().getRawMessage("email.password-reset-content"),
                        "Hello %name%!<br>Password reset code: <b>%code%</b>");

        sendEmail(
                email,
                rawSubject.replace("%server%", senderName),
                rawContent
                        .replace("%server%", GeneralUtil.escapeHtml(senderName))
                        .replace("%code%", GeneralUtil.escapeHtml(token))
                        .replace("%ip%", GeneralUtil.escapeHtml(ip))
                        .replace("%name%", GeneralUtil.escapeHtml(username)));
    }

    @Override
    public void sendVerificationMail(String email, String token, String username) {
        String senderName =
                java.util.Objects.requireNonNullElse(
                        plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER), "LibreLogin");
        String rawSubject =
                java.util.Objects.requireNonNullElse(
                        plugin.getMessages().getRawMessage("email.verification-subject"),
                        "Verify your email on %server%");
        String rawContent =
                java.util.Objects.requireNonNullElse(
                        plugin.getMessages().getRawMessage("email.verification-content"),
                        "Hello %name%!<br>Email verification code: <b>%code%</b>");

        sendEmail(
                email,
                rawSubject.replace("%server%", senderName),
                rawContent
                        .replace("%name%", GeneralUtil.escapeHtml(username))
                        .replace("%server%", GeneralUtil.escapeHtml(senderName))
                        .replace("%code%", GeneralUtil.escapeHtml(token)));
    }
}
