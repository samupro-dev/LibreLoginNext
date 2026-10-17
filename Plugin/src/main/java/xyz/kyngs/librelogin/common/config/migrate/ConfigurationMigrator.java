/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config.migrate;

import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.common.config.ConfigurateHelper;

/**
 * Strategy interface for migrating outdated configuration options and nodes across revision
 * upgrades.
 */
public interface ConfigurationMigrator {

    /**
     * Executes migration logic on the target configuration node.
     *
     * @param helper the configuration helper
     * @param logger the plugin logger
     */
    void migrate(ConfigurateHelper helper, Logger logger);
}
