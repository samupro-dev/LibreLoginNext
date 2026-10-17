/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command;

import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;

/**
 * Exception thrown when a user enters invalid arguments or fails validation during command
 * execution. The wrapped {@link TextComponent} contains the formatted error message intended for
 * display to the issuer.
 */
public class InvalidCommandArgument extends RuntimeException {

    private final TextComponent errorMessage;

    /**
     * Constructs a new command argument exception with the specified user-facing message.
     *
     * @param errorMessage the component message to display to the user
     */
    public InvalidCommandArgument(TextComponent errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Retrieves the formatted error component to send to the command issuer.
     *
     * @return the error message component
     */
    public TextComponent getErrorMessage() {
        return errorMessage;
    }
}
