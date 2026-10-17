/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;

/**
 * Declares all localized message keys, fallback default messages, and descriptive comments for
 * messages.conf.
 */
@SuppressWarnings("unused")
public class MessageKeys {

    private MessageKeys() {}

    public static final ConfigurationKey<String> KICK_DATABASE_ERROR =
            new ConfigurationKey<>(
                    "kick.database-error",
                    "Database connection is currently unavailable. Please try again later!",
                    "Disconnected when the database connection pool is offline or unreachable"
                            + " during login.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_PREMIUM_ERROR_THROTTLED =
            new ConfigurationKey<>(
                    "kick.premium-error-throttled",
                    "The Mojang API is rate limiting our server, please try joining again in a"
                            + " while!",
                    "Disconnected when Mojang API rate-limits profile verification.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_PREMIUM_ERROR_UNDEFINED =
            new ConfigurationKey<>(
                    "kick.premium-error-undefined",
                    "There was an issue while communicating with Mojang. If this persists, please"
                            + " contact server administration.",
                    "Disconnected when an unexpected error occurs while communicating with Mojang"
                            + " API.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_PREMIUM_INFO_ENABLED =
            new ConfigurationKey<>(
                    "kick.premium-info-enabled",
                    "<green>Autologin enabled! Please reconnect to authenticate via"
                            + " Mojang.</green>",
                    "Disconnected upon enabling Mojang autologin to initialize encryption"
                            + " handshake.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_PREMIUM_INFO_DISABLED =
            new ConfigurationKey<>(
                    "kick.premium-info-disabled",
                    "<yellow>Autologin disabled! Please reconnect to log in with your"
                            + " password.</yellow>",
                    "Disconnected upon disabling autologin to switch account mode back to manual"
                            + " password.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_INVALID_CASE_USERNAME =
            new ConfigurationKey<>(
                    "kick.invalid-case-username",
                    "Please connect using the exact registered casing: <gold>%username%</gold>",
                    "Disconnected when username casing does not match the registered profile.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_OCCUPIED_USERNAME =
            new ConfigurationKey<>(
                    "kick.occupied-username",
                    "The username <gold>%username%</gold> is already registered to another"
                            + " account.",
                    "Disconnected when an unregistered player attempts to join with a username"
                            + " already taken.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_ILLEGAL_USERNAME =
            new ConfigurationKey<>(
                    "kick.illegal-username",
                    "Your username contains invalid characters or exceeds 16 characters.",
                    "Disconnected when username contains forbidden characters or exceeds 16"
                            + " characters.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_NAME_MISMATCH =
            new ConfigurationKey<>(
                    "kick.name-mismatch",
                    "A premium player with autologin enabled uses the nickname"
                            + " <gold>%nickname%</gold>. Please contact server administration to"
                            + " resolve this profile conflict.",
                    "Disconnected during a profile collision between cracked and online premium"
                            + " accounts.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_SHORT_USERNAME =
            new ConfigurationKey<>(
                    "kick.short-username",
                    "Your username is too short! Minimum allowed length is %length% characters.",
                    "Disconnected when username is shorter than the configured minimum length.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_IP_LIMIT =
            new ConfigurationKey<>(
                    "kick.ip-limit",
                    "You have reached the maximum allowed accounts for your IP address.",
                    "Disconnected when IP address has exceeded the maximum registered accounts"
                            + " limit.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_NO_LOBBY =
            new ConfigurationKey<>(
                    "kick.no-lobby",
                    "There is no available lobby server to connect you to. Please try again later.",
                    "Disconnected when no backend lobby servers are reachable or configured.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_NO_LIMBO =
            new ConfigurationKey<>(
                    "kick.no-limbo",
                    "There is no available authentication server available. Please contact server"
                            + " administration.",
                    "Disconnected when no limbo authentication servers are reachable.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_TIME_LIMIT =
            new ConfigurationKey<>(
                    "kick.time-limit",
                    "You took too long to authenticate! Please reconnect and log in.",
                    "Disconnected when exceeding the allowed login/registration time window.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_ERROR_PASSWORD_WRONG =
            new ConfigurationKey<>(
                    "kick.error-password-wrong",
                    "Incorrect password!",
                    "Disconnected after exceeding the maximum allowed incorrect password attempts.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_ERROR_TOTP_WRONG =
            new ConfigurationKey<>(
                    "kick.error-totp-wrong",
                    "Too many failed 2FA attempts!",
                    "Disconnected after exceeding the maximum allowed incorrect 2FA attempts.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_ERROR_IP_RATE_LIMITED =
            new ConfigurationKey<>(
                    "kick.error-ip-rate-limited",
                    "Too many failed authentication attempts from your IP address. Please try again"
                            + " later.",
                    "Disconnected when an IP address exceeds the brute-force attempt rate limit.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_2FA_ENABLED =
            new ConfigurationKey<>(
                    "kick.2fa-enabled",
                    "<green>Two-Factor Authentication enabled! Please reconnect to"
                            + " authenticate.</green>",
                    "Disconnected upon completing 2FA TOTP enablement.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_ALREADY_CONNECTED =
            new ConfigurationKey<>(
                    "kick.already-connected",
                    "You are already connected to this server!",
                    "Disconnected when a player with the same identity is already connected.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_UNREGISTERED =
            new ConfigurationKey<>(
                    "kick.unregistered",
                    "<yellow>Your account registration has been removed successfully.</yellow>",
                    "Disconnected after successfully unregistering the account.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_FROM_FLOODGATE =
            new ConfigurationKey<>(
                    "error.from-floodgate",
                    "This command cannot be used by Floodgate/Bedrock accounts.",
                    "Command execution blocked for players connected via Bedrock/Floodgate proxy"
                            + " bridges.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NO_EMAIL =
            new ConfigurationKey<>(
                    "error.no-email",
                    "You do not have a recovery email address linked to your account.",
                    "Attempting password recovery on an account without a configured recovery"
                            + " email.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_ALREADY_AUTHORIZED =
            new ConfigurationKey<>(
                    "error.already-authorized",
                    "You are already authenticated!",
                    "Attempting to run /login or /register while already authenticated.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_ALREADY_REGISTERED =
            new ConfigurationKey<>(
                    "error.already-registered",
                    "You are already registered! Use /login to authenticate.",
                    "Attempting to register when the account is already registered.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NOT_AUTHORIZED =
            new ConfigurationKey<>(
                    "error.not-authorized",
                    "Please log in or register before using commands!",
                    "Attempting to run restricted commands before authenticating.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NOT_REGISTERED =
            new ConfigurationKey<>(
                    "error.not-registered",
                    "This account is not registered yet! Please use /register.",
                    "Attempting to log in on an account that has not been registered yet.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NOT_PAID =
            new ConfigurationKey<>(
                    "error.not-paid",
                    "This username does not exist in the official Mojang database!",
                    "Attempting to enable premium mode on a username that does not exist in"
                            + " Mojang's database.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NOT_PREMIUM =
            new ConfigurationKey<>(
                    "error.not-premium",
                    "You do not have autologin enabled! Enable it using /premium <password>.",
                    "Attempting to disable premium autologin on an account that is already in"
                            + " cracked mode.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NOT_CRACKED =
            new ConfigurationKey<>(
                    "error.not-cracked",
                    "You already have autologin enabled! Disable it using /cracked.",
                    "Attempting to enable premium autologin on an account that already has it"
                            + " enabled.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PASSWORD_WRONG =
            new ConfigurationKey<>(
                    "error.password-wrong",
                    "Incorrect password!",
                    "Incorrect password entered during login or command verification.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NO_PASSWORD =
            new ConfigurationKey<>(
                    "error.no-password",
                    "You do not have a password configured. Use /cracked to switch to manual login"
                            + " mode.",
                    "Attempting to change password on an autologin premium account without password"
                            + " credentials.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PASSWORD_NOT_MATCH =
            new ConfigurationKey<>(
                    "error.password-not-match",
                    "Passwords do not match!",
                    "Password confirmation mismatch during registration or password change.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_FORBIDDEN_PASSWORD =
            new ConfigurationKey<>(
                    "error.forbidden-password",
                    "The password is too short or is not allowed.",
                    "Password does not meet the minimum length or contains forbidden patterns.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PASSWORD_TOO_LONG =
            new ConfigurationKey<>(
                    "error.password-too-long",
                    "The specified password is too long!",
                    "Password exceeds maximum allowed storage length.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PASSWORD_CORRUPTED =
            new ConfigurationKey<>(
                    "error.password-corrupted",
                    "Your password record is corrupted. Please contact a server administrator.",
                    "Corrupted hash stored in the database.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PREMIUM_THROTTLED =
            new ConfigurationKey<>(
                    "error.premium-throttled",
                    "The Mojang API is rate limiting our server. Please try the command again in a"
                            + " few moments.",
                    "Mojang API rate limit encountered during in-game command execution.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PREMIUM_UNKNOWN =
            new ConfigurationKey<>(
                    "error.premium-unknown",
                    "An error occurred while communicating with the Mojang API. Check console logs"
                            + " for details.",
                    "Unexpected Mojang API communication error.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_THROTTLE =
            new ConfigurationKey<>(
                    "error.throttle",
                    "You are sending commands too quickly. Please wait a moment.",
                    "Player sending authentication commands too quickly (command rate limiting).",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NO_CONFIRM =
            new ConfigurationKey<>(
                    "error.no-confirm",
                    "Please run /premium <password> first before confirming!",
                    "Executing /premium confirm without initiating /premium <password> first.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NO_MAIL_CONFIRM =
            new ConfigurationKey<>(
                    "error.no-mail-confirm",
                    "Please run /email set <address> <password> first!",
                    "Executing /email verify before initiating /email set.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NO_PASSWORD_RESET =
            new ConfigurationKey<>(
                    "error.no-password-reset",
                    "Please run /email reset first!",
                    "Executing /email reset confirm before requesting /email reset.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_MAIL_THROTTLE =
            new ConfigurationKey<>(
                    "error.mail-throttle",
                    "You are sending email requests too fast. Please wait a moment.",
                    "Exceeding the email rate limit.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_WRONG_MAIL_VERIFY =
            new ConfigurationKey<>(
                    "error.wrong-mail-verify",
                    "Incorrect email verification code!",
                    "Incorrect 6-digit email verification code entered.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_WRONG_PASSWORD_RESET =
            new ConfigurationKey<>(
                    "error.wrong-password-reset",
                    "Incorrect password reset code!",
                    "Incorrect password recovery reset code entered.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_MAIL_DISABLED =
            new ConfigurationKey<>(
                    "error.mail-disabled",
                    "Email functionality is disabled on this server.",
                    "Attempting to use email commands when email service is disabled in"
                            + " configuration.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_CORRUPTED_MESSAGES =
            new ConfigurationKey<>(
                    "error.corrupted-messages",
                    "messages.conf syntax is corrupted; retaining existing messages. Cause:"
                            + " %cause%",
                    "Configuration or message file syntax corruption warnings.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_CORRUPTED_CONFIGURATION =
            new ConfigurationKey<>(
                    "error.corrupted-configuration",
                    "config.conf syntax is corrupted; retaining existing configuration. Cause:"
                            + " %cause%",
                    "Configuration file syntax corruption warnings.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_UNKNOWN =
            new ConfigurationKey<>(
                    "error.unknown",
                    "An unexpected error occurred. Please check console logs for details.",
                    "General unexpected error fallback.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_UNKNOWN_USER =
            new ConfigurationKey<>(
                    "error.unknown-user",
                    "The specified user does not exist in the database!",
                    "Administrative target user lookup failures.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_OCCUPIED_USER =
            new ConfigurationKey<>(
                    "error.occupied-user",
                    "The target username is already occupied by another account!",
                    "Attempting to migrate to an occupied nickname.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PLAYER_ONLINE =
            new ConfigurationKey<>(
                    "error.player-online",
                    "The specified player is currently online!",
                    "Administrative actions requiring offline target player.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PLAYER_OFFLINE =
            new ConfigurationKey<>(
                    "error.player-offline",
                    "The specified player is currently offline!",
                    "Administrative actions requiring online target player.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PLAYER_AUTHORIZED =
            new ConfigurationKey<>(
                    "error.player-authorized",
                    "The specified player is already authenticated!",
                    "Administrative login command executed on authorized player.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PLAYER_NOT_REGISTERED =
            new ConfigurationKey<>(
                    "error.player-not-registered",
                    "The specified player is not registered!",
                    "Administrative action on unregistered player.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NOT_AVAILABLE_ON_MULTI_PROXY =
            new ConfigurationKey<>(
                    "error.not-available-on-multi-proxy",
                    "This feature is not available in a Multi-Proxy cluster configuration.",
                    "Feature disabled in multi-proxy environment.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_PASSWORD_RESETTING_DISABLED =
            new ConfigurationKey<>(
                    "error.password-resetting-disabled",
                    "Password recovery via email is currently disabled on this server.",
                    "Password reset attempted when email recovery service is disabled.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_NO_PERMISSION =
            new ConfigurationKey<>(
                    "error.no-permission",
                    "<red>You do not have permission to execute this command.</red>",
                    "Missing permission node.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_INVALID_SYNTAX =
            new ConfigurationKey<>(
                    "error.invalid-syntax",
                    "Usage: <c2>{command}</c2> <c3>{syntax}</c3>",
                    "Invalid command argument syntax.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_UNKNOWN_COMMAND =
            new ConfigurationKey<>(
                    "error.unknown-command",
                    "Unknown command. Type \"/help\" for assistance.",
                    "Unknown command executed.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_MAIL_NOT_SENT =
            new ConfigurationKey<>(
                    "error.mail-not-sent",
                    "Failed to send email. Please verify your address or contact a server"
                            + " administrator.",
                    "SMTP email delivery failure.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_2FA_NOT_ENABLED =
            new ConfigurationKey<>(
                    "error.2fa-not-enabled",
                    "Two-factor authentication is not enabled on this account.",
                    "Attempting to disable 2FA on an account without 2FA enabled.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_COMMAND_DISABLED =
            new ConfigurationKey<>(
                    "error.command-disabled",
                    "This command is currently disabled by server administration.",
                    "Executing a command that has been disabled in the plugin configuration.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_KICK =
            new ConfigurationKey<>(
                    "info.kick",
                    "You have been disconnected: %reason%",
                    "Generic kick notification wrapper.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_LOGGING_IN =
            new ConfigurationKey<>(
                    "info.logging-in",
                    "Logging in...",
                    "Authentication progress feedback. Set to single space \" \" to disable.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_LOGGED_IN =
            new ConfigurationKey<>(
                    "info.logged-in",
                    "<green>Successfully logged in!</green>",
                    "Authentication successful message.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_PREMIUM_LOGGED_IN =
            new ConfigurationKey<>(
                    "info.premium-logged-in",
                    "<green>Authenticated automatically via Mojang!</green>",
                    "Mojang autologin successful message.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_SESSION_LOGGED_IN =
            new ConfigurationKey<>(
                    "info.session-logged-in",
                    "<green>Authenticated automatically via active session!</green>",
                    "Session resumption successful message.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_REGISTERING =
            new ConfigurationKey<>(
                    "info.registering",
                    "Registering...",
                    "Registration progress feedback. Set to single space \" \" to disable.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_REGISTERED =
            new ConfigurationKey<>(
                    "info.registered",
                    "<green>Account successfully registered!</green>",
                    "Account registered confirmation message.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_ENABLING =
            new ConfigurationKey<>(
                    "info.enabling",
                    "Enabling...",
                    "Enabling operation progress feedback.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_DISABLING =
            new ConfigurationKey<>(
                    "info.disabling",
                    "Disabling...",
                    "Disabling operation progress feedback.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_2FA_DISABLED =
            new ConfigurationKey<>(
                    "info.2fa-disabled",
                    "<green>Two-Factor Authentication disabled successfully!</green>",
                    "Feedback sent when two-factor authentication is disabled.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_EDITING =
            new ConfigurationKey<>(
                    "info.editing",
                    "Editing...",
                    "Administrative editing progress feedback.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_EDITED =
            new ConfigurationKey<>(
                    "info.edited",
                    "<green>Updated successfully!</green>",
                    "Administrative modification confirmation message.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_RESETTING_PASSWORD =
            new ConfigurationKey<>(
                    "info.resetting-password",
                    "Resetting password...",
                    "Password reset progress feedback.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_PASSWORD_RESET =
            new ConfigurationKey<>(
                    "info.password-reset",
                    "<green>Password reset successfully! You can now log in with your new"
                            + " password.</green>",
                    "Password reset success message.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_DUMPING =
            new ConfigurationKey<>(
                    "info.dumping",
                    "Generating diagnostic system dump...",
                    "Diagnostic dump generation message.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_DUMPED =
            new ConfigurationKey<>(
                    "info.dumped",
                    "<green>Diagnostic dump saved to %file%!</green>",
                    "Diagnostic dump generated confirmation.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_DELETING =
            new ConfigurationKey<>(
                    "info.deleting",
                    "Deleting...",
                    "Account deletion progress feedback.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_DELETED =
            new ConfigurationKey<>(
                    "info.deleted",
                    "<green>Deleted successfully!</green>",
                    "Account deletion confirmation message.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_RELOADING =
            new ConfigurationKey<>(
                    "info.reloading",
                    "Reloading configuration...",
                    "Reload progress feedback.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_RELOADED =
            new ConfigurationKey<>(
                    "info.reloaded",
                    "<green>Configuration and messages successfully reloaded!</green>",
                    "Configuration reload success confirmation.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_SENDING_EMAIL =
            new ConfigurationKey<>(
                    "info.sending-email",
                    "Sending email...",
                    "Email dispatch progress feedback.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_SENT_EMAIL =
            new ConfigurationKey<>(
                    "info.sent-email",
                    "<green>Email sent successfully!</green>",
                    "Email dispatch success confirmation.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_USER =
            new ConfigurationKey<>(
                    "info.user",
                    """
                    Name: %name%
                    UUID: %uuid%
                    Premium UUID: %premium_uuid%
                    Last Seen: %last_seen%
                    Joined: %joined%
                    2FA: %2fa%
                    EMail: %email%
                    IP: %ip%
                    Last Authenticated: %last_authenticated%\
                    """,
                    "User profile inspection header (/librelogin user info).",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_USER_FORGOTTEN =
            new ConfigurationKey<>(
                    "info.user-forgotten",
                    "<green>User %name% and all associated personal records have been permanently"
                            + " erased.</green>",
                    "GDPR Right to be Forgotten confirmation.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_ABOUT =
            new ConfigurationKey<>(
                    "info.about",
                    """
                    This server is running LibreLogin Next.
                    Version: %version%
                    Authors: samupro-dev, and contributors
                    Source: https://github.com/samupro-dev/LibreLoginNext
                    License: Mozilla Public License 2.0\
                    """,
                    "/librelogin about attribution output.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_NO_ALTS =
            new ConfigurationKey<>(
                    "info.no-alts",
                    "No alternate accounts found sharing this IP address or hash.",
                    "Alternate accounts inspection (/librelogin user alts).",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_ALTS =
            new ConfigurationKey<>(
                    "info.alts",
                    "Found %count% alternate accounts linked to this IP:",
                    "Alternate accounts list header.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_ALTS_ENTRY =
            new ConfigurationKey<>(
                    "info.alts-entry",
                    "- %name% (last seen: %last_seen%)",
                    "Alternate accounts list item.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_MAIL_SENDING =
            new ConfigurationKey<>(
                    "info.mail-sending",
                    "Sending confirmation email...",
                    "Email self-service status notifications.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_MAIL_VERIFIED =
            new ConfigurationKey<>(
                    "info.mail-verified",
                    "<green>Email verified! You can now use your email to reset your password if"
                            + " needed.</green>",
                    "Email verification success message.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_VERIFICATION_MAIL_SENT =
            new ConfigurationKey<>(
                    "info.verification-mail-sent",
                    "<green>Verification email sent! Check your inbox (or spam folder). You have 10"
                            + " minutes to verify.</green>",
                    "Email verification sent confirmation.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_RESET_PASSWORD_MAIL_SENT =
            new ConfigurationKey<>(
                    "info.reset-password-mail-sent",
                    "<green>Password reset email sent! Check your inbox (or spam folder). You have"
                            + " 10 minutes to reset.</green>",
                    "Password reset email sent confirmation.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> HEALTH_HEADER =
            new ConfigurationKey<>(
                    "health.header",
                    "&8========== &bLibreLogin Next Health &8==========",
                    "Health command output header.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> HEALTH_PLATFORM =
            new ConfigurationKey<>(
                    "health.platform",
                    "&7Platform: &f%platform% &8| &7Version: &b%version%",
                    "Health command platform information line.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> HEALTH_MEMORY =
            new ConfigurationKey<>(
                    "health.memory",
                    "&7JVM Memory: &e%used%MB &7/ &a%max%MB &8(&6%free%MB free&8)",
                    "Health command JVM memory status line.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> HEALTH_DATABASE =
            new ConfigurationKey<>(
                    "health.database",
                    "&7Database: &a%status% &8(&f%type%&8)",
                    "Health command database connectivity line.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> HEALTH_CACHES =
            new ConfigurationKey<>(
                    "health.caches",
                    "&7Caches: &f%users% cached users",
                    "Health command user cache statistics line.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> HEALTH_FOOTER =
            new ConfigurationKey<>(
                    "health.footer",
                    "&8==========================================",
                    "Health command output footer.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> PROMPT_REGISTER =
            new ConfigurationKey<>(
                    "prompt.register",
                    "Please register using: &e/register &b<password> <password>",
                    "Periodic chat reminders for unregistered players.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> PROMPT_LOGIN =
            new ConfigurationKey<>(
                    "prompt.login",
                    "Please log in using: &e/login &b<password> [2fa_code]",
                    "Periodic chat reminders for unauthenticated players.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> PROMPT_CONFIRM =
            new ConfigurationKey<>(
                    "prompt.confirm",
                    "<yellow>You are enabling Mojang autologin. You will"
                        + " <red><bold>NOT</bold></red> be able to connect from a cracked client."
                        + " Use <gold>/cracked</gold> to revert. Run <gold>/premium confirm</gold>"
                        + " within 5 minutes to confirm.</yellow>",
                    "Confirmation prompt for /premium.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TITLE_REGISTER =
            new ConfigurationKey<>(
                    "title.register",
                    "&6&lRegister",
                    "On-screen title for registration.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TITLE_LOGIN =
            new ConfigurationKey<>(
                    "title.login",
                    "&6&lLogin",
                    "On-screen title for login.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SUB_TITLE_REGISTER =
            new ConfigurationKey<>(
                    "subtitle.register",
                    "&e/register &b<password> <password>",
                    "On-screen subtitle for registration.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SUB_TITLE_LOGIN =
            new ConfigurationKey<>(
                    "subtitle.login",
                    "&e/login &b<password>",
                    "On-screen subtitle for login.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ACTION_BAR_REGISTER =
            new ConfigurationKey<>(
                    "action-bar.register",
                    "&e/register &b<password> <password>",
                    "Action bar reminder for registration.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ACTION_BAR_LOGIN =
            new ConfigurationKey<>(
                    "action-bar.login",
                    "&e/login &b<password>",
                    "Action bar reminder for login.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TOTP_NOT_PROVIDED =
            new ConfigurationKey<>(
                    "totp.not-provided",
                    "This account requires 2FA! Use: /login <password> <2fa_code>",
                    "Missing 2FA code during login.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TOTP_WRONG =
            new ConfigurationKey<>(
                    "totp.wrong",
                    "Incorrect 2FA code!",
                    "Incorrect 2FA code entered.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_CRACKED_REQUIRES_PASSWORD =
            new ConfigurationKey<>(
                    "error.cracked-requires-password",
                    "Your account has no password yet. Use /cracked <password> <password> to set"
                            + " one while switching to cracked mode.",
                    "Running /cracked on a premium account that has no password.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_CRACKED_REQUIRES_PASSWORD_STAFF =
            new ConfigurationKey<>(
                    "error.cracked-requires-password-staff",
                    "This user has no password. Set one first with /librelogin user setpassword,"
                            + " otherwise anyone could claim the account.",
                    "Running /librelogin user cracked on a user that has no password.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_INTERNAL_ERROR =
            new ConfigurationKey<>(
                    "kick.internal-error",
                    "An internal error occurred. Please try again later.",
                    "Unexpected error while handling a login.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_HANDSHAKE_FAILED =
            new ConfigurationKey<>(
                    "kick.handshake-failed",
                    "Your login could not be verified. Please reconnect.",
                    "Premium (online-mode) handshake failed; the detail is in the debug log.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_INVALID_SESSION =
            new ConfigurationKey<>(
                    "kick.invalid-session",
                    "Could not verify your Minecraft session. Restart your launcher and try again.",
                    "Mojang did not confirm the session of a premium player.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_SESSION_VERIFY_FAILED =
            new ConfigurationKey<>(
                    "kick.session-verify-failed",
                    "Could not reach the Minecraft session servers. Please try again in a moment.",
                    "The Mojang session server could not be reached.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_LOGGED_IN_ELSEWHERE =
            new ConfigurationKey<>(
                    "kick.logged-in-elsewhere",
                    "You logged in from another location.",
                    "Shown on the other proxy when the account logs in elsewhere (multi-proxy).",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_REMOTE_DEFAULT =
            new ConfigurationKey<>(
                    "kick.remote-default",
                    "Disconnected by an administrator.",
                    "Remote kick without a reason (multi-proxy).",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_PROXY_SHUTDOWN =
            new ConfigurationKey<>(
                    "kick.proxy-shutdown",
                    "The proxy is shutting down. Please try again later.",
                    "Velocity is stopped because LibreLogin Next could not start.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_KICK_UNKNOWN_REASON =
            new ConfigurationKey<>(
                    "info.kick-unknown-reason",
                    "unknown reason",
                    "Value of %reason% in info.kick when the server gave none.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_ALTS_MORE =
            new ConfigurationKey<>(
                    "info.alts-more",
                    "<gray>... and %count% more",
                    "Last line of /librelogin user alts when the list is cut.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_VALUE_NA =
            new ConfigurationKey<>(
                    "info.value-na",
                    "N/A",
                    "Placeholder value for missing data in staff output.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_VALUE_ENABLED =
            new ConfigurationKey<>(
                    "info.value-enabled",
                    "Enabled",
                    "Placeholder value for an enabled setting in staff output.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> INFO_VALUE_DISABLED =
            new ConfigurationKey<>(
                    "info.value-disabled",
                    "Disabled",
                    "Placeholder value for a disabled setting in staff output.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> HEALTH_STATUS_CONNECTED =
            new ConfigurationKey<>(
                    "health.status-connected",
                    "Connected",
                    "Database status in /librelogin health.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> HEALTH_STATUS_DISCONNECTED =
            new ConfigurationKey<>(
                    "health.status-disconnected",
                    "Disconnected",
                    "Database status in /librelogin health.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_LOGIN_LOCKED =
            new ConfigurationKey<>(
                    "error.login-locked",
                    "Too many failed attempts on this account. Try again in %seconds% seconds.",
                    "Escalating per-account lockout after repeated wrong passwords.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> ERROR_INVALID_EMAIL =
            new ConfigurationKey<>(
                    "error.invalid-email",
                    "That is not a valid email address.",
                    "Email syntax check in /email set and /librelogin user setemail.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> EMAIL_TEST_SUBJECT =
            new ConfigurationKey<>(
                    "email.test-subject",
                    "LibreLogin Next test mail",
                    "Subject of /librelogin email test.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> EMAIL_TEST_CONTENT =
            new ConfigurationKey<>(
                    "email.test-content",
                    "Congratulations! You have successfully configured email sending in LibreLogin"
                        + " Next!<br>Now, your users can reset their passwords.<br><i>If you have"
                        + " no idea what this means, block the sender.</i>",
                    "HTML body of /librelogin email test.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_SERVER_BUSY =
            new ConfigurationKey<>(
                    "kick.server-busy",
                    "The server is busy processing logins. Please try again in a moment.",
                    "Login work queue is full (for example during a join flood).",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> KICK_TOO_MANY_CONNECTIONS =
            new ConfigurationKey<>(
                    "kick.too-many-connections",
                    "Too many connection attempts from your address. Please wait a few seconds.",
                    "Per-IP pre-login rate limit exceeded.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TOTP_ALREADY_ENABLED =
            new ConfigurationKey<>(
                    "totp.already-enabled",
                    "2FA is already enabled on your account. Disable it first with /2fa disable"
                            + " <password>.",
                    "Running /2fa while a 2FA secret is already set.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TOTP_UNAVAILABLE =
            new ConfigurationKey<>(
                    "totp.unavailable",
                    "This account requires 2FA, but 2FA is currently unavailable on this server."
                            + " Please contact an administrator.",
                    "Login refused because the account has 2FA but TOTP is disabled or"
                            + " unavailable.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TOTP_NOT_AWAITING =
            new ConfigurationKey<>(
                    "totp.not-awaiting",
                    "You have not initiated 2FA setup! Run /2fa to begin.",
                    "Attempting /2fa confirm without running /2fa first.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TOTP_SHOW_INFO =
            new ConfigurationKey<>(
                    "totp.show-info",
                    """
                    Please scan the QR code displayed on the map with your authenticator app (Google Authenticator, 2FAS, Aegis, Microsoft Authenticator, etc.).
                    Once added, run /2fa confirm <code> to finish activation.
                    Disconnect to cancel.\
                    """,
                    "QR code map presentation instructions.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TOTP_SHOW_INFO_MANUAL =
            new ConfigurationKey<>(
                    "totp.show-info-manual",
                    """
                    Add the secret key below to your authenticator app (Google Authenticator, 2FAS, Aegis, Microsoft Authenticator, etc.).
                    Once added, run /2fa confirm <code> to finish activation.
                    Disconnect to cancel.\
                    """,
                    "2FA setup instructions when no QR code map can be shown (Paper, or Velocity"
                            + " without Protocolize).",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TOTP_GENERATING =
            new ConfigurationKey<>(
                    "totp.generating",
                    "Generating 2FA setup map...",
                    "QR code generation progress.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> TOTP_SECRET_KEY =
            new ConfigurationKey<>(
                    "totp.secret-key",
                    "<gold>Secret Key: <yellow><bold>%code%</bold></yellow> <gray>(copy or add"
                            + " manually into authenticator app)</gray>",
                    "Manual 2FA secret key displayed in chat for clients unable to render maps or"
                            + " for manual authenticator entry.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> EMAIL_VERIFICATION_SUBJECT =
            new ConfigurationKey<>(
                    "email.verification-subject",
                    "Verify your email on %server%",
                    "Subject line for account email verification messages.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> EMAIL_VERIFICATION_CONTENT =
            new ConfigurationKey<>(
                    "email.verification-content",
                    """
                    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 540px; margin: 0 auto; padding: 24px; border: 1px solid #e1e4e8; border-radius: 8px; background-color: #ffffff;">
                        <h2 style="color: #0366d6; margin-top: 0;">Email Verification</h2>
                        <p>Hello <b>%name%</b>,</p>
                        <p>You requested to link this email address to your account on <b>%server%</b>.</p>
                        <p>To confirm, please run the following command in-game:</p>
                        <div style="background-color: #f6f8fa; padding: 12px 16px; border-radius: 6px; font-family: monospace; font-size: 16px; font-weight: bold; color: #24292e; text-align: center; margin: 20px 0;">
                            /email verify %code%
                        </div>
                        <p style="color: #586069; font-size: 13px;">This verification code expires in 10 minutes. If you did not request this, please ignore this email.</p>
                    </div>\
                    """,
                    "HTML template body for email address verification emails.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> EMAIL_PASSWORD_RESET_SUBJECT =
            new ConfigurationKey<>(
                    "email.password-reset-subject",
                    "Reset your password on %server%",
                    "Subject line for self-service password recovery emails.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> EMAIL_PASSWORD_RESET_CONTENT =
            new ConfigurationKey<>(
                    "email.password-reset-content",
                    """
                    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 540px; margin: 0 auto; padding: 24px; border: 1px solid #e1e4e8; border-radius: 8px; background-color: #ffffff;">
                        <h2 style="color: #d73a49; margin-top: 0;">Password Reset Request</h2>
                        <p>Hello <b>%name%</b>,</p>
                        <p>A password reset request was initiated for your account on <b>%server%</b> (Request IP: <code>%ip%</code>).</p>
                        <p>To set a new password, execute the following command in-game:</p>
                        <div style="background-color: #f6f8fa; padding: 12px 16px; border-radius: 6px; font-family: monospace; font-size: 15px; font-weight: bold; color: #24292e; text-align: center; margin: 20px 0;">
                            /email reset confirm %code% &lt;new_password&gt; &lt;new_password&gt;
                        </div>
                        <p style="color: #586069; font-size: 13px;">This reset code expires in 10 minutes. If you did not request this, your account is safe and you can disregard this email.</p>
                    </div>\
                    """,
                    "HTML template body for self-service password reset emails.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_LOGIN =
            new ConfigurationKey<>(
                    "syntax.login",
                    "<password> [2fa_code]",
                    "Command syntax parameter hint for /login.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_REGISTER =
            new ConfigurationKey<>(
                    "syntax.register",
                    "<password> <passwordRepeat>",
                    "Command syntax parameter hint for /register.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_UNREGISTER =
            new ConfigurationKey<>(
                    "syntax.unregister",
                    "<password>",
                    "Command syntax parameter hint for /unregister.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_PREMIUM =
            new ConfigurationKey<>(
                    "syntax.premium",
                    "<password>",
                    "Command syntax parameter hint for /premium.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_PREMIUM_CONFIRM =
            new ConfigurationKey<>(
                    "syntax.premium-confirm",
                    "",
                    "Command syntax parameter hint for /premium confirm.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_CHANGE_PASSWORD =
            new ConfigurationKey<>(
                    "syntax.change-password",
                    "<oldPassword> <newPassword>",
                    "Command syntax parameter hint for /changepassword.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_2FA_CONFIRM =
            new ConfigurationKey<>(
                    "syntax.2fa-confirm",
                    "<code>",
                    "Command syntax parameter hint for /2fa confirm.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_2FA_DISABLE =
            new ConfigurationKey<>(
                    "syntax.2fa-disable",
                    "<password>",
                    "Command syntax parameter hint for /2fa disable.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_EMAIL_SET =
            new ConfigurationKey<>(
                    "syntax.email-set",
                    "<address> <password>",
                    "Command syntax parameter hint for /email set.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_EMAIL_VERIFY =
            new ConfigurationKey<>(
                    "syntax.email-verify",
                    "<code>",
                    "Command syntax parameter hint for /email verify.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_EMAIL_RESET =
            new ConfigurationKey<>(
                    "syntax.email-reset",
                    "",
                    "Command syntax parameter hint for /email reset.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_EMAIL_RESET_CONFIRM =
            new ConfigurationKey<>(
                    "syntax.email-reset-confirm",
                    "<code> <password> <passwordRepeat>",
                    "Command syntax parameter hint for /email reset confirm.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_EMAIL_TEST =
            new ConfigurationKey<>(
                    "syntax.email-test",
                    "<address>",
                    "Command syntax parameter hint for /librelogin email test.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_INFO =
            new ConfigurationKey<>(
                    "syntax.user-info",
                    "<name>",
                    "Command syntax parameter hint for /librelogin user info.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_MIGRATE =
            new ConfigurationKey<>(
                    "syntax.user-migrate",
                    "<name> <newName>",
                    "Command syntax parameter hint for /librelogin user migrate.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_UNREGISTER =
            new ConfigurationKey<>(
                    "syntax.user-unregister",
                    "<name>",
                    "Command syntax parameter hint for /librelogin user unregister.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_DELETE =
            new ConfigurationKey<>(
                    "syntax.user-delete",
                    "<name>",
                    "Command syntax parameter hint for /librelogin user delete.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_FORGET =
            new ConfigurationKey<>(
                    "syntax.user-forget",
                    "<name>",
                    "Command syntax parameter hint for /librelogin user forget.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_PREMIUM =
            new ConfigurationKey<>(
                    "syntax.user-premium",
                    "<name>",
                    "Command syntax parameter hint for /librelogin user premium.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_CRACKED =
            new ConfigurationKey<>(
                    "syntax.user-cracked",
                    "<name>",
                    "Command syntax parameter hint for /librelogin user cracked.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_REGISTER =
            new ConfigurationKey<>(
                    "syntax.user-register",
                    "<name> <password>",
                    "Command syntax parameter hint for /librelogin user register.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_LOGIN =
            new ConfigurationKey<>(
                    "syntax.user-login",
                    "<name>",
                    "Command syntax parameter hint for /librelogin user login.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_REMOVE_2FA =
            new ConfigurationKey<>(
                    "syntax.user-remove-2fa",
                    "<name>",
                    "Command syntax parameter hint for /librelogin user remove2fa.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_REMOVE_EMAIL =
            new ConfigurationKey<>(
                    "syntax.user-remove-email",
                    "<name>",
                    "Command syntax parameter hint for /librelogin user removeemail.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_SET_EMAIL =
            new ConfigurationKey<>(
                    "syntax.user-set-email",
                    "<name> <email>",
                    "Command syntax parameter hint for /librelogin user setemail.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_SET_PASSWORD =
            new ConfigurationKey<>(
                    "syntax.user-set-password",
                    "<name> <newPassword>",
                    "Command syntax parameter hint for /librelogin user setpassword.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> SYNTAX_USER_ALTS =
            new ConfigurationKey<>(
                    "syntax.user-alts",
                    "<name>",
                    "Command syntax parameter hint for /librelogin user alts.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_LOGIN =
            new ConfigurationKey<>(
                    "autocomplete.login",
                    "password",
                    "Autocomplete placeholder hint for /login.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_REGISTER =
            new ConfigurationKey<>(
                    "autocomplete.register",
                    "password password",
                    "Autocomplete placeholder hint for /register.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_UNREGISTER =
            new ConfigurationKey<>(
                    "autocomplete.unregister",
                    "password",
                    "Autocomplete placeholder hint for /unregister.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_PREMIUM =
            new ConfigurationKey<>(
                    "autocomplete.premium",
                    "password",
                    "Autocomplete placeholder hint for /premium.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_CHANGE_PASSWORD =
            new ConfigurationKey<>(
                    "autocomplete.change-password",
                    "oldPassword newPassword",
                    "Autocomplete placeholder hint for /changepassword.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_2FA_CONFIRM =
            new ConfigurationKey<>(
                    "autocomplete.2fa-confirm",
                    "code",
                    "Autocomplete placeholder hint for /2fa confirm.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_2FA_DISABLE =
            new ConfigurationKey<>(
                    "autocomplete.2fa-disable",
                    "password",
                    "Autocomplete placeholder hint for /2fa disable.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_EMAIL_SET =
            new ConfigurationKey<>(
                    "autocomplete.email-set",
                    "address password",
                    "Autocomplete placeholder hint for /email set.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_EMAIL_VERIFY =
            new ConfigurationKey<>(
                    "autocomplete.email-verify",
                    "code",
                    "Autocomplete placeholder hint for /email verify.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_EMAIL_RESET =
            new ConfigurationKey<>(
                    "autocomplete.email-reset",
                    "",
                    "Autocomplete placeholder hint for /email reset.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_EMAIL_RESET_CONFIRM =
            new ConfigurationKey<>(
                    "autocomplete.email-reset-confirm",
                    "code newPassword newPassword",
                    "Autocomplete placeholder hint for /email reset confirm.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_EMAIL_TEST =
            new ConfigurationKey<>(
                    "autocomplete.email-test",
                    "address",
                    "Autocomplete placeholder hint for /librelogin email test.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_INFO =
            new ConfigurationKey<>(
                    "autocomplete.user-info",
                    "@players",
                    "Autocomplete placeholder hint for /librelogin user info.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_MIGRATE =
            new ConfigurationKey<>(
                    "autocomplete.user-migrate",
                    "@players newName",
                    "Autocomplete placeholder hint for /librelogin user migrate.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_UNREGISTER =
            new ConfigurationKey<>(
                    "autocomplete.user-unregister",
                    "@players",
                    "Autocomplete placeholder hint for /librelogin user unregister.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_DELETE =
            new ConfigurationKey<>(
                    "autocomplete.user-delete",
                    "@players",
                    "Autocomplete placeholder hint for /librelogin user delete.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_FORGET =
            new ConfigurationKey<>(
                    "autocomplete.user-forget",
                    "@players",
                    "Autocomplete placeholder hint for /librelogin user forget.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_PREMIUM =
            new ConfigurationKey<>(
                    "autocomplete.user-premium",
                    "@players",
                    "Autocomplete placeholder hint for /librelogin user premium.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_CRACKED =
            new ConfigurationKey<>(
                    "autocomplete.user-cracked",
                    "@players",
                    "Autocomplete placeholder hint for /librelogin user cracked.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_REGISTER =
            new ConfigurationKey<>(
                    "autocomplete.user-register",
                    "@players password",
                    "Autocomplete placeholder hint for /librelogin user register.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_LOGIN =
            new ConfigurationKey<>(
                    "autocomplete.user-login",
                    "@players",
                    "Autocomplete placeholder hint for /librelogin user login.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_REMOVE_2FA =
            new ConfigurationKey<>(
                    "autocomplete.user-remove-2fa",
                    "@players",
                    "Autocomplete placeholder hint for /librelogin user remove2fa.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_REMOVE_EMAIL =
            new ConfigurationKey<>(
                    "autocomplete.user-remove-email",
                    "@players",
                    "Autocomplete placeholder hint for /librelogin user removeemail.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_SET_EMAIL =
            new ConfigurationKey<>(
                    "autocomplete.user-set-email",
                    "@players email",
                    "Autocomplete placeholder hint for /librelogin user setemail.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_SET_PASSWORD =
            new ConfigurationKey<>(
                    "autocomplete.user-set-password",
                    "@players newPassword",
                    "Autocomplete placeholder hint for /librelogin user setpassword.",
                    ConfigurateHelper::getString);

    public static final ConfigurationKey<String> AUTOCOMPLETE_USER_ALTS =
            new ConfigurationKey<>(
                    "autocomplete.user-alts",
                    "@players",
                    "Autocomplete placeholder hint for /librelogin user alts.",
                    ConfigurateHelper::getString);
}
