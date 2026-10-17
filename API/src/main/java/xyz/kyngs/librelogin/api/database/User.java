/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.database;

import java.sql.Timestamp;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;

/** Domain model representing a user account stored within the database. */
public interface User {

    /**
     * Gets the base32 secret key used for TOTP two-factor authentication.
     *
     * @return the secret key, or null if 2FA is not configured
     */
    @Nullable
    String getSecret();

    /**
     * Sets the base32 secret key used for TOTP two-factor authentication.
     *
     * @param secret the secret key to set, or null to remove
     */
    void setSecret(@Nullable String secret);

    /**
     * Gets the last known IP address of the user.
     *
     * @return the IP address string, or null if not yet recorded
     */
    @Nullable
    String getIp();

    /**
     * Sets the last known IP address of the user.
     *
     * @param ip the IP address to set
     */
    void setIp(@Nullable String ip);

    /**
     * Gets the identifier of the last backend server the user was connected to.
     *
     * @return the server name, or null if unknown
     */
    @Nullable
    String getLastServer();

    /**
     * Sets the identifier of the last backend server the user was connected to.
     *
     * @param lastServer the server name to set
     */
    void setLastServer(@Nullable String lastServer);

    /**
     * Gets the timestamp of the last successful authentication.
     *
     * @return the last authentication timestamp, or null if never authenticated
     */
    @Nullable
    Timestamp getLastAuthentication();

    /**
     * Sets the timestamp of the last successful authentication.
     *
     * @param lastAuthentication the authentication timestamp to set
     */
    void setLastAuthentication(@Nullable Timestamp lastAuthentication);

    /**
     * Gets the timestamp when the user was initially created or registered.
     *
     * @return the join date timestamp
     */
    Timestamp getJoinDate();

    /**
     * Sets the timestamp when the user was initially created or registered.
     *
     * @param joinDate the join date timestamp
     */
    void setJoinDate(Timestamp joinDate);

    /**
     * Gets the timestamp when the user was last seen on the network.
     *
     * @return the last seen timestamp
     */
    Timestamp getLastSeen();

    /**
     * Sets the timestamp when the user was last seen on the network.
     *
     * @param lastSeen the last seen timestamp
     */
    void setLastSeen(Timestamp lastSeen);

    /**
     * Gets the stored hashed password record for the user.
     *
     * @return the hashed password, or null if not registered or passwordless
     */
    @Nullable
    HashedPassword getHashedPassword();

    /**
     * Sets the stored hashed password record for the user.
     *
     * @param hashedPassword the hashed password to set
     */
    void setHashedPassword(@Nullable HashedPassword hashedPassword);

    /**
     * Gets the unique identifier (UUID) of the user account.
     *
     * @return the user UUID
     */
    UUID getUuid();

    /**
     * Gets the official Mojang UUID if the user has premium authentication enabled.
     *
     * @return the premium UUID, or null if not linked
     */
    @Nullable
    UUID getPremiumUUID();

    /**
     * Sets the official Mojang UUID for premium authentication.
     *
     * @param premiumUUID the premium UUID to set, or null to unlink
     */
    void setPremiumUUID(@Nullable UUID premiumUUID);

    /**
     * Gets the last known player username/nickname.
     *
     * @return the username
     */
    String getLastNickname();

    /**
     * Sets the last known player username/nickname.
     *
     * @param lastNickname the username to set
     */
    void setLastNickname(String lastNickname);

    /**
     * Checks whether the user has completed registration (has a password set).
     *
     * @return true if registered, false otherwise
     */
    boolean isRegistered();

    /**
     * Checks whether automatic premium login is enabled for this user.
     *
     * @return true if auto-login is active, false otherwise
     */
    boolean autoLoginEnabled();

    /**
     * Gets the email address linked to the user account.
     *
     * @return the email address, or null if not set
     */
    @Nullable
    String getEmail();

    /**
     * Sets the email address linked to the user account.
     *
     * @param email the email address to set, or null to remove
     */
    void setEmail(@Nullable String email);
}
