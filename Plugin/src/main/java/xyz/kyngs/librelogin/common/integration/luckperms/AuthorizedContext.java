/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.integration.luckperms;

import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;

/**
 * LuckPerms {@link ContextCalculator} providing the {@code librelogin-authorized} context, which
 * indicates whether a player has completed authentication.
 *
 * @param <P> the platform-specific player type
 */
public class AuthorizedContext<P> implements ContextCalculator<P> {

    private static final String KEY = "librelogin-authorized";

    private final AuthenticLibreLogin<P, ?> plugin;

    /**
     * Constructs a new authorized context calculator.
     *
     * @param plugin the LibreLogin plugin instance
     */
    public AuthorizedContext(AuthenticLibreLogin<P, ?> plugin) {
        this.plugin = plugin;
    }

    @Override
    public void calculate(@NotNull P player, @NotNull ContextConsumer consumer) {
        consumer.accept(
                KEY, Boolean.toString(plugin.getAuthorizationProvider().isAuthorized(player)));
    }

    @Override
    public @NotNull ContextSet estimatePotentialContexts() {
        return ImmutableContextSet.builder().add(KEY, "true").add(KEY, "false").build();
    }
}
