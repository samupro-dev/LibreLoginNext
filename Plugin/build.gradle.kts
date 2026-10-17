import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.gradleup.shadow")
}

/*
 * Dependency variant selection.
 *
 * Every library bundled into the final jar must run on Java 17, so the runtime classpaths request
 * Java 17 variants and Gradle rejects any dependency that requires a newer runtime. The remaining
 * classpaths request Java 25 because the compile-only platform APIs (Paper, Velocity) are published
 * for newer Java versions and only have to be readable by the compiler.
 */
configurations.matching { it.isCanBeResolved }.configureEach {
    attributes {
        attribute(
            TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            if (name == "runtimeClasspath" || name == "testRuntimeClasspath") 17 else 25
        )
    }
}

/*
 * Velocity reads the plugin version from the @Plugin annotation of VelocityBootstrap. The source
 * file contains an @version@ placeholder; this task writes a copy with the project version that is
 * compiled instead of the original.
 */
val generateVelocityBootstrap = tasks.register<Copy>("generateVelocityBootstrap") {
    // Resolved during configuration so the filter does not reference the project at execution time.
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)

    from("src/main/java") {
        include("xyz/kyngs/librelogin/velocity/VelocityBootstrap.java")
    }
    into(layout.buildDirectory.dir("generated/sources/versioned/java"))
    filter { line -> line.replace("@version@", pluginVersion) }
}

sourceSets {
    main {
        java {
            srcDir(generateVelocityBootstrap)
        }
    }
}

tasks.compileJava {
    // Exclude the original VelocityBootstrap source; the generated copy is compiled instead.
    val mainJavaDir = project.file("src/main/java").absolutePath.replace('\\', '/')
    exclude { element ->
        element.file.absolutePath.replace('\\', '/').startsWith(mainJavaDir) &&
            element.relativePath.toString() == "xyz/kyngs/librelogin/velocity/VelocityBootstrap.java"
    }
}

tasks.withType<ShadowJar>().configureEach {
    destinationDirectory = rootProject.layout.projectDirectory.dir("target")
    archiveBaseName = "LibreLoginNext"
    archiveClassifier = ""

    // Provided at runtime by Paper and Velocity.
    dependencies {
        exclude(dependency("org.slf4j:.*:.*"))
        exclude(dependency("org.checkerframework:.*:.*"))
        exclude(dependency("com.google.errorprone:.*:.*"))
        exclude(dependency("com.google.protobuf:.*:.*"))
        exclude(dependency("com.google.guava:.*:.*"))
        exclude(dependency("com.google.code.gson:.*:.*"))
    }

    // Bundled libraries are relocated to avoid conflicts with other plugins and the server.
    relocate("co.aikar.commands", "xyz.kyngs.librelogin.lib.acf")
    relocate("co.aikar.locales", "xyz.kyngs.librelogin.lib.locales")
    relocate("com.github.benmanes.caffeine", "xyz.kyngs.librelogin.lib.caffeine")
    relocate("com.typesafe.config", "xyz.kyngs.librelogin.lib.hocon")
    relocate("com.zaxxer.hikari", "xyz.kyngs.librelogin.lib.hikari")
    relocate("org.mariadb", "xyz.kyngs.librelogin.lib.mariadb")
    relocate("org.postgresql", "xyz.kyngs.librelogin.lib.postgresql")
    relocate("org.intellij", "xyz.kyngs.librelogin.lib.intellij")
    relocate("org.jetbrains", "xyz.kyngs.librelogin.lib.jetbrains")
    relocate("io.leangen.geantyref", "xyz.kyngs.librelogin.lib.reflect")
    relocate("org.spongepowered.configurate", "xyz.kyngs.librelogin.lib.configurate")
    // Configurate's copy of kyori-option; the copy used by Adventure is relocated in :Adventure.
    relocate("net.kyori.option", "xyz.kyngs.librelogin.lib.configurate.option")
    relocate("io.lettuce.core", "xyz.kyngs.librelogin.lib.lettuce")
    relocate("io.lettuce.authx", "xyz.kyngs.librelogin.lib.lettuce.authx")
    relocate("com.password4j", "xyz.kyngs.librelogin.lib.password4j")
    relocate("com.helger.totp", "xyz.kyngs.librelogin.lib.totp")
    relocate("org.apache.commons.codec", "xyz.kyngs.librelogin.lib.codec")
    relocate("com.google.zxing", "xyz.kyngs.librelogin.lib.zxing")
    relocate("org.apache.commons.mail", "xyz.kyngs.librelogin.lib.mail")

    /*
     * Service provider files are merged and rewritten to the relocated class names. Duplicates
     * must reach the transformer, so these files use the INCLUDE strategy while every other entry
     * keeps the default EXCLUDE strategy.
     */
    mergeServiceFiles()
    filesMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

tasks.withType<Jar>().configureEach {
    // Mozilla Public License 2.0 from the repository root, shipped with every published artifact.
    // Stored as LICENSE.txt: bundled dependencies already contribute a LICENSE entry.
    from(rootProject.layout.projectDirectory.file("LICENSE")) {
        rename { "LICENSE.txt" }
    }
}

dependencies {
    // Public API
    implementation(project(":API"))

    // Velocity (annotation processor generates velocity-plugin.json)
    annotationProcessor("com.velocitypowered:velocity-api:4.1.0-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-api:4.1.0-SNAPSHOT")

    // Redis client, relocated in :Lettuce together with its own Netty and Reactor
    implementation(project(path = ":Lettuce", configuration = "relocated"))

    // Databases
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.10")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")

    // Command framework
    implementation("co.aikar:acf-velocity:0.5.1-SNAPSHOT")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")

    // Utilities and cryptography
    implementation("com.github.ben-manes.caffeine:caffeine:3.3.1")
    implementation("org.spongepowered:configurate-hocon:4.2.0")
    implementation("com.password4j:password4j:1.8.4")
    implementation("com.helger:ph-totp:2.1.1")
    implementation("commons-codec:commons-codec:1.23.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.zxing:javase:3.5.4") {
        exclude(group = "org.jcommander", module = "jcommander")
        exclude(group = "com.beust", module = "jcommander")
        exclude(group = "com.github.jai-imageio", module = "jai-imageio-core")
    }
    implementation("org.apache.commons:commons-email:1.6.0")

    // Platform and integration APIs, provided at runtime by the server or other plugins
    compileOnly("com.google.guava:guava:32.1.3-jre")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    compileOnly("dev.simplix:protocolize-api:2.4.2")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("org.apache.logging.log4j:log4j-core:2.26.1")

    // Tests
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Reference data for the map packet ID tests
    testImplementation("dev.simplix:protocolize-api:2.4.2")
    testImplementation("com.github.retrooper:packetevents-spigot:2.14.0")
    // Platform-provided libraries; Gson 2.8.0 is the oldest version shipped by a supported server
    testRuntimeOnly("com.google.guava:guava:32.1.3-jre")
    testRuntimeOnly("com.google.code.gson:gson:2.8.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<ProcessResources>().configureEach {
    // Resolved during configuration; declared as an input so a version change re-runs the task.
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)

    // config.conf and messages.conf are generated at runtime from the configuration keys.
    exclude("config.conf", "messages.conf")

    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
        expand(mapOf("version" to pluginVersion))
    }
}
