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
 * LuckPerms {@link ContextCalculator} providing the {@code librelogin-awaiting2fa} context, which
 * indicates whether a player is currently awaiting two-factor verification.
 *
 * @param <P> the platform-specific player type
 */
public class Awaiting2FAContext<P> implements ContextCalculator<P> {

    private static final String KEY = "librelogin-awaiting2fa";

    private final AuthenticLibreLogin<P, ?> plugin;

    /**
     * Constructs a new awaiting-2FA context calculator.
     *
     * @param plugin the LibreLogin plugin instance
     */
    public Awaiting2FAContext(AuthenticLibreLogin<P, ?> plugin) {
        this.plugin = plugin;
    }

    @Override
    public void calculate(@NotNull P player, @NotNull ContextConsumer consumer) {
        consumer.accept(
                KEY, Boolean.toString(plugin.getAuthorizationProvider().isAwaiting2FA(player)));
    }

    @Override
    public @NotNull ContextSet estimatePotentialContexts() {
        return ImmutableContextSet.builder().add(KEY, "true").add(KEY, "false").build();
    }
}
