plugins {
    id("java-library")
}

dependencies {
    api("org.jetbrains:annotations:26.1.0")
    // Relocated Adventure types (xyz.kyngs.librelogin.lib.adventure) exposed by the public API.
    api(project(path = ":Adventure", configuration = "relocated"))
    compileOnly("com.google.guava:guava:32.1.3-jre")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    // Keep parameter names in the class files for API consumers.
    options.compilerArgs.add("-parameters")
}

tasks.withType<Jar>().configureEach {
    archiveBaseName = "LibreLoginNext-API"
    // Mozilla Public License 2.0 from the repository root, shipped with every published artifact.
    // Stored as LICENSE.txt: bundled dependencies already contribute a LICENSE entry.
    from(rootProject.layout.projectDirectory.file("LICENSE")) {
        rename { "LICENSE.txt" }
    }
}

tasks.test {
    useJUnitPlatform()
}
