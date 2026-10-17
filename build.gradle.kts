import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    id("com.diffplug.spotless") version "8.10.4"
    // Applied by the modules that produce shaded jars; the version is managed here only.
    id("com.gradleup.shadow") version "9.6.1" apply false
}

defaultTasks("shadowJar")

version = "0.26.0"

subprojects {
    /*
     * Java versions.
     *
     * Sources are compiled with a JDK 25 toolchain so the build can use current tooling and read
     * platform APIs published for newer Java versions. Every class file is emitted with
     * `--release 17`, which makes the plugin loadable on Java 17+ servers and limits the code to
     * the Java 17 platform API.
     */
    val toolchainJavaVersion = 25
    val targetJavaRelease = 17

    version = rootProject.version
    group = "xyz.kyngs.librelogin"

    apply(plugin = "com.diffplug.spotless")

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(toolchainJavaVersion)
            }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.isDeprecation = true
        options.release = targetJavaRelease
    }

    extensions.configure<SpotlessExtension> {
        java {
            googleJavaFormat()
                .aosp()
                .reflowLongStrings()
                .reorderImports(false)
        }
    }
}
