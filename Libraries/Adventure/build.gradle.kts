import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

/*
 * Relocated Adventure and MiniMessage.
 *
 * LibreLogin compiles against these relocated classes and bundles them on every platform, so text
 * parsing never depends on the Adventure version shipped by the server. Components are converted to
 * the platform representation only when they are delivered to a player or the console.
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
    implementation("net.kyori:adventure-api:4.26.1")
    implementation("net.kyori:adventure-text-minimessage:4.26.1")
    implementation("net.kyori:adventure-text-serializer-legacy:4.26.1")
    implementation("net.kyori:adventure-text-serializer-plain:4.26.1")
    implementation("net.kyori:adventure-text-serializer-gson:4.26.1")
}

// Consumed by :API and :Plugin to depend on the relocated jar.
val relocated = configurations.create("relocated") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = "relocated"

    dependencies {
        // Gson is provided by every supported platform (Gson 2.8.0+ on Paper 1.13 and newer).
        exclude(dependency("com.google.code.gson:.*:.*"))
        exclude(dependency("org.jetbrains:annotations:.*"))
        exclude(dependency("org.jspecify:.*:.*"))
    }

    relocate("net.kyori.adventure", "xyz.kyngs.librelogin.lib.adventure")
    relocate("net.kyori.examination", "xyz.kyngs.librelogin.lib.examination")
    relocate("net.kyori.option", "xyz.kyngs.librelogin.lib.option")
    relocate("net.kyori", "xyz.kyngs.librelogin.lib.kyori")

    exclude("module-info.class", "META-INF/versions/*/module-info.class")

    // Service provider files are merged and rewritten to the relocated class names.
    mergeServiceFiles()
    filesMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

artifacts {
    add(relocated.name, tasks.named("shadowJar"))
}
