/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.crypto;

import xyz.kyngs.librelogin.api.crypto.CryptoProvider;

/**
 * Marks a provider that only verifies imported hashes. It can never be the default provider, and a
 * password it verified is re-hashed with the default provider on the next successful login.
 */
public interface VerifyOnlyCryptoProvider extends CryptoProvider {}
