import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

/*
 * Lettuce bundled with a private, relocated copy of Netty and Reactor.
 *
 * Lettuce 7.8 requires Netty 4.2, while older Paper and Velocity builds load Netty 4.1 first. Only
 * Lettuce uses this private copy; the rest of LibreLogin keeps using the server's Netty. Native
 * transports are excluded, so Lettuce runs on the NIO transport.
 */

plugins {
    id("java")
    id("com.gradleup.shadow")
}

// The bundled libraries must run on Java 17.
configurations.runtimeClasspath {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
}

dependencies {
    implementation("io.lettuce:lettuce-core:7.9.0.RELEASE")
}

// Consumed by :Plugin to depend on the relocated jar.
val relocated = configurations.create("relocated") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = "relocated"

    dependencies {
        exclude(dependency("io.netty:netty-transport-native-.*:.*"))
        exclude(dependency("io.netty:netty-transport-classes-.*:.*"))
        exclude(dependency("io.netty:netty-tcnative.*:.*"))
        exclude(dependency("org.jetbrains:annotations:.*"))
        exclude(dependency("org.jspecify:.*:.*"))
    }

    relocate("io.netty", "xyz.kyngs.librelogin.lib.netty")
    relocate("reactor", "xyz.kyngs.librelogin.lib.reactor")
    relocate("org.reactivestreams", "xyz.kyngs.librelogin.lib.reactivestreams")
    relocate("redis.clients.authentication", "xyz.kyngs.librelogin.lib.redisauth")

    exclude("module-info.class", "META-INF/versions/*/module-info.class", "META-INF/native/**")

    // Service provider files are merged and rewritten to the relocated class names.
    mergeServiceFiles()
    filesMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

artifacts {
    add(relocated.name, tasks.named("shadowJar"))
}
