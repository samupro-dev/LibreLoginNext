/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.integration.luckperms;

import net.luckperms.api.LuckPermsProvider;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;

/**
 * Manages the integration with the LuckPerms permissions framework, registering and unregistering
 * custom context calculators for authorization state and two-factor verification state.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class LuckPermsIntegration<P, S> {

    private final AuthorizedContext<P> authorizedContext;
    private final Awaiting2FAContext<P> awaiting2faContext;

    /**
     * Constructs and initializes the LuckPerms integration, registering context calculators.
     *
     * @param plugin the LibreLogin plugin instance
     */
    public LuckPermsIntegration(AuthenticLibreLogin<P, S> plugin) {
        authorizedContext = new AuthorizedContext<>(plugin);
        awaiting2faContext = new Awaiting2FAContext<>(plugin);
        var contextMgr = LuckPermsProvider.get().getContextManager();
        contextMgr.registerCalculator(authorizedContext);
        contextMgr.registerCalculator(awaiting2faContext);
    }

    /** Unregisters all registered context calculators from the LuckPerms context manager. */
    public void disable() {
        var contextMgr = LuckPermsProvider.get().getContextManager();
        contextMgr.unregisterCalculator(authorizedContext);
        contextMgr.unregisterCalculator(awaiting2faContext);
    }
}
