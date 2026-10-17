/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.listener;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.api.event.events.WrongPasswordEvent;
import xyz.kyngs.librelogin.api.event.events.WrongPasswordEvent.AuthenticationSource;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;

public class LoginTryListener<P, S> {

    private final AuthenticLibreLogin<P, S> plugin;
    private final Cache<UUID, Integer> loginTries;
    private final Cache<String, Integer> loginTriesByIp;
    // Escalating per-account lockout: each time the attempt limit is hit, the account cannot try
    // again for 30 s, 60 s, 120 s, ... up to 15 min. Reset by a successful login.
    private final Cache<UUID, Lockout> lockouts =
            Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.DAYS).maximumSize(50_000).build();

    private static final long BASE_LOCKOUT_MILLIS = 30_000;
    private static final long MAX_LOCKOUT_MILLIS = 15 * 60_000;

    private record Lockout(int strikes, long untilMillis) {}

    public LoginTryListener(AuthenticLibreLogin<P, S> libreLogin) {
        this.plugin = libreLogin;
        this.loginTries =
                Caffeine.newBuilder()
                        .expireAfterWrite(
                                plugin.getConfiguration()
                                        .get(
                                                ConfigurationKeys
                                                        .MILLISECONDS_TO_EXPIRE_LOGIN_ATTEMPTS),
                                TimeUnit.MILLISECONDS)
                        .maximumSize(10_000)
                        .build();
        this.loginTriesByIp =
                Caffeine.newBuilder()
                        .expireAfterWrite(
                                plugin.getConfiguration()
                                        .get(ConfigurationKeys.IP_ATTEMPTS_EXPIRE_MILLIS),
                                TimeUnit.MILLISECONDS)
                        .maximumSize(50_000)
                        .build();
        libreLogin
                .getEventProvider()
                .subscribe(libreLogin.getEventTypes().wrongPassword, this::onWrongPassword);
        libreLogin
                .getEventProvider()
                .subscribe(libreLogin.getEventTypes().authenticated, this::onAuthenticated);
    }

    private void onWrongPassword(WrongPasswordEvent<P, S> wrongPasswordEvent) {
        // Every password or code check counts: commands run by an authorized session must not be
        // usable as an unlimited password oracle.
        AuthenticationSource source = wrongPasswordEvent.getSource();

        var rawIp = plugin.getPlatformHandle().getIP(wrongPasswordEvent.getPlayer());
        var ip = plugin.anonymizeIP(rawIp);
        int maxIpAttempts = plugin.getConfiguration().get(ConfigurationKeys.MAX_IP_LOGIN_ATTEMPTS);
        if (maxIpAttempts > 0 && ip != null) {
            int currentIpTries = loginTriesByIp.asMap().merge(ip, 1, Integer::sum);
            if (currentIpTries >= maxIpAttempts) {
                plugin.getPlatformHandle()
                        .kick(
                                wrongPasswordEvent.getPlayer(),
                                plugin.getMessages().getMessage("kick.error-ip-rate-limited"));
                return;
            }
        }

        if (plugin.getConfiguration().get(ConfigurationKeys.MAX_LOGIN_ATTEMPTS) == -1) return;
        var uuid = plugin.getPlatformHandle().getUUIDForPlayer(wrongPasswordEvent.getPlayer());
        if (uuid == null) return;
        int currentLoginTry = loginTries.asMap().merge(uuid, 1, Integer::sum);
        if (currentLoginTry
                >= plugin.getConfiguration().get(ConfigurationKeys.MAX_LOGIN_ATTEMPTS)) {
            loginTries.invalidate(uuid);
            lockouts.asMap()
                    .compute(
                            uuid,
                            (key, previous) -> {
                                int strikes = previous == null ? 1 : previous.strikes() + 1;
                                long duration =
                                        Math.min(
                                                MAX_LOCKOUT_MILLIS,
                                                BASE_LOCKOUT_MILLIS << Math.min(strikes - 1, 20));
                                return new Lockout(strikes, System.currentTimeMillis() + duration);
                            });
            String kickMessage =
                    source == AuthenticationSource.TOTP
                            ? "kick.error-totp-wrong"
                            : "kick.error-password-wrong";
            var component = plugin.getMessages().getMessage(kickMessage);
            if (component == null) {
                component = plugin.getMessages().getMessage("kick.error-password-wrong");
            }
            if (component == null) {
                component =
                        xyz.kyngs.librelogin.lib.adventure.text.Component.text(
                                "Too many failed authentication attempts!");
            }
            plugin.getPlatformHandle().kick(wrongPasswordEvent.getPlayer(), component);
        }
    }

    /**
     * Returns how long the account must wait before the next password attempt.
     *
     * @param uuid the account UUID
     * @return remaining lockout in seconds, or 0 when not locked
     */
    public long remainingLockoutSeconds(UUID uuid) {
        if (uuid == null) return 0;
        var lockout = lockouts.getIfPresent(uuid);
        if (lockout == null) return 0;
        long remaining = lockout.untilMillis() - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    private void onAuthenticated(AuthenticatedEvent<P, S> authenticatedEvent) {
        var uuid = plugin.getPlatformHandle().getUUIDForPlayer(authenticatedEvent.getPlayer());
        if (uuid != null) {
            loginTries.invalidate(uuid);
            lockouts.invalidate(uuid);
        }
        // Do not invalidate loginTriesByIp here: allow natural TTL decay to prevent resetting
        // attempts on shared NAT IPs
    }
}
