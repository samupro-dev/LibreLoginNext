/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PacketEvents is optional on Velocity, whose injector reads every field and method signature of
 * the plugin classes: a PacketEvents type there (or a class implementing one) stops LibreLogin from
 * loading when PacketEvents is not installed. The class files are read as bytes, so the classes are
 * not loaded (velocity-api is not on the test classpath).
 */
class PacketEventsIsolationTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "xyz/kyngs/librelogin/common/AuthenticLibreLogin.class",
                "xyz/kyngs/librelogin/velocity/VelocityLibreLogin.class",
                "xyz/kyngs/librelogin/velocity/VelocityBootstrap.class"
            })
    void pluginSignaturesDoNotReferencePacketEvents(String classFile) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(classFile)) {
            assertNotNull(in, classFile);
            var bytes = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            // Descriptors ("Lpkg/Name;") are what signatures use; plain class references in method
            // bodies are only resolved when that code runs.
            for (var forbidden :
                    new String[] {
                        "Lcom/github/retrooper/",
                        "Lio/github/retrooper/",
                        "Lxyz/kyngs/librelogin/common/dialog/PacketEventsAuthDialogs;",
                        "Lxyz/kyngs/librelogin/velocity/PacketChatBlocker;"
                    }) {
                assertFalse(bytes.contains(forbidden), classFile + " references " + forbidden);
            }
        }
    }
}
