/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.server;

/**
 * Encapsulates status and ping response information for a backend server.
 *
 * @param maxPlayers the maximum player capacity reported by the server
 */
public record ServerPing(int maxPlayers) {}
