/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.log;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Security log filter that intercepts server log records and prevents sensitive credential-bearing
 * commands (passwords, 2FA tokens, email addresses) from leaking into server logs or console
 * output.
 */
public abstract class LogFilter {

    private static final Set<String> PROTECTED_ROOT_COMMANDS =
            Set.of(
                    "login",
                    "l",
                    "register",
                    "reg",
                    "premium",
                    "2fa",
                    "totp",
                    "changepassword",
                    "changepass",
                    "email",
                    "unregister",
                    "cracked");

    private static final Set<String> PROTECTED_SUB_COMMANDS =
            Set.of(
                    "librelogin user register",
                    "librelogin user setpassword",
                    "librelogin user setemail");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // The root also accepts "." and ":" so namespaced forms such as "/librelogin:login" are caught.
    private static final Pattern COMMAND_PATTERN =
            Pattern.compile(
                    "(?i)(?:issued server command:\\s*|executed command:\\s*|executed"
                            + " command\\s*|command:\\s*)/([a-zA-Z0-9_.:-]+)(?:\\s+(.*))?");

    /**
     * Inspects a log message string and determines whether it is safe to be recorded.
     *
     * @param message the raw log message text
     * @return {@code true} if the message is safe and should be logged, {@code false} if it should
     *     be suppressed
     */
    protected boolean checkMessage(String message) {
        if (message == null || message.isEmpty()) {
            return true;
        }

        // Suppress benign legacy warnings if encountered
        if (message.contains(
                        "Plugin listener xyz.kyngs.librelogin.bungeecord.BungeeCordListener took")
                || message.contains("Duplicate key name")) {
            return false;
        }

        // Every server log line passes here: skip the regex unless the line mentions a command.
        if (message.indexOf("ommand") < 0 && message.indexOf("OMMAND") < 0) {
            return true;
        }

        Matcher matcher = COMMAND_PATTERN.matcher(message);
        if (matcher.find()) {
            String rootCommand = matcher.group(1).toLowerCase(Locale.ROOT);
            int namespace = rootCommand.lastIndexOf(':');
            if (namespace >= 0) {
                rootCommand = rootCommand.substring(namespace + 1);
            }
            if (PROTECTED_ROOT_COMMANDS.contains(rootCommand)) {
                return false;
            }

            if (rootCommand.equals("librelogin") || rootCommand.equals("ll")) {
                String arguments = matcher.group(2);
                if (arguments != null) {
                    String lowerArgs =
                            WHITESPACE
                                    .matcher(arguments.toLowerCase(Locale.ROOT).trim())
                                    .replaceAll(" ");
                    for (String sub : PROTECTED_SUB_COMMANDS) {
                        if (lowerArgs.startsWith(sub.substring(sub.indexOf(' ') + 1))) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    /** Injects this filter into the underlying platform logging pipeline. */
    public abstract void inject();

    /**
     * Removes this filter from the platform logging pipeline (on disable, so reloads do not stack
     * filters or keep the old plugin class loader alive).
     */
    public abstract void remove();
}
