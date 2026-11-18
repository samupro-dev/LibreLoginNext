/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;

class ConfigurateHelperTest {

    @Test
    void readsDriverPropertiesAsStringMap() throws Exception {
        var root = CommentedConfigurationNode.root();
        root.node("database", "properties", "mysql", "properties", "allowPublicKeyRetrieval")
                .set("true");
        root.node("database", "properties", "mysql", "properties", "connectTimeout").set(5000);
        var helper = new ConfigurateHelper(root);

        assertEquals(
                Map.of("allowPublicKeyRetrieval", "true", "connectTimeout", "5000"),
                helper.getStringMap("database.properties.mysql.properties"));
        assertNull(helper.getStringMap("database.properties.postgresql.properties"));
        // A nested map is not a valid property value.
        assertThrows(
                RuntimeException.class, () -> helper.getStringMap("database.properties.mysql"));
    }
}
