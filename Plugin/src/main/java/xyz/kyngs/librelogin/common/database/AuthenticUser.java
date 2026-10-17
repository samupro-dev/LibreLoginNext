/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.database;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.User;

/**
 * Standard implementation of the {@link User} entity representing player authentication and profile
 * data stored in the database.
 */
public class AuthenticUser implements User {

    // Fields are read and written from several threads (commands, listeners, cache): volatile
    // gives every reader the latest value of each field.

    private final UUID uuid;
    private volatile UUID premiumUUID;
    private volatile HashedPassword hashedPassword;
    private volatile String lastNickname;
    private volatile Timestamp joinDate;
    private volatile Timestamp lastSeen;
    private volatile String secret;
    private volatile String ip;
    private volatile Timestamp lastAuthentication;
    private volatile String lastServer;
    private volatile String email;

    /**
     * Constructs a new {@link AuthenticUser} with all required and optional attributes.
     *
     * @param uuid the unique identifier of the user
     * @param premiumUUID the official Mojang UUID if premium, or null
     * @param hashedPassword the hashed password credential, or null if unregistered
     * @param lastNickname the last known username
     * @param joinDate the timestamp when the user first joined
     * @param lastSeen the timestamp when the user was last seen
     * @param secret the TOTP secret key, or null
     * @param ip the last known IP address
     * @param lastAuthentication the timestamp of the last successful authentication
     * @param lastServer the name of the last connected server
     * @param email the associated email address, or null
     */
    public AuthenticUser(
            UUID uuid,
            UUID premiumUUID,
            HashedPassword hashedPassword,
            String lastNickname,
            Timestamp joinDate,
            Timestamp lastSeen,
            String secret,
            String ip,
            Timestamp lastAuthentication,
            String lastServer,
            String email) {
        this.uuid = uuid;
        this.premiumUUID = premiumUUID;
        this.hashedPassword = hashedPassword;
        this.lastNickname = lastNickname;
        this.joinDate = joinDate;
        this.lastSeen = lastSeen;
        this.secret = secret;
        this.ip = ip;
        this.lastAuthentication = lastAuthentication;
        this.lastServer = lastServer;
        this.email = email;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public UUID getPremiumUUID() {
        return premiumUUID;
    }

    @Override
    public void setPremiumUUID(UUID premiumUUID) {
        this.premiumUUID = premiumUUID;
    }

    @Override
    public HashedPassword getHashedPassword() {
        return hashedPassword;
    }

    @Override
    public void setHashedPassword(HashedPassword hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    @Override
    public String getLastNickname() {
        return lastNickname;
    }

    @Override
    public void setLastNickname(String lastNickname) {
        this.lastNickname = lastNickname;
    }

    @Override
    public Timestamp getJoinDate() {
        return joinDate;
    }

    @Override
    public void setJoinDate(Timestamp joinDate) {
        this.joinDate = joinDate;
    }

    @Override
    public Timestamp getLastSeen() {
        return lastSeen;
    }

    @Override
    public void setLastSeen(Timestamp lastSeen) {
        this.lastSeen = lastSeen;
    }

    @Override
    public String getSecret() {
        return secret;
    }

    @Override
    public void setSecret(String secret) {
        this.secret = secret;
    }

    @Override
    public String getIp() {
        return ip;
    }

    @Override
    public void setIp(String ip) {
        this.ip = ip;
    }

    @Override
    public Timestamp getLastAuthentication() {
        return lastAuthentication;
    }

    @Override
    public void setLastAuthentication(Timestamp lastAuthentication) {
        this.lastAuthentication = lastAuthentication;
    }

    @Override
    public String getLastServer() {
        return lastServer;
    }

    @Override
    public void setLastServer(String lastServer) {
        this.lastServer = lastServer;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public boolean isRegistered() {
        return hashedPassword != null;
    }

    @Override
    public boolean autoLoginEnabled() {
        return premiumUUID != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var user = (AuthenticUser) o;
        return uuid.equals(user.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
