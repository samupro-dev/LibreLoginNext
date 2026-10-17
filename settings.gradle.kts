pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

/*
 * Centralised repository declaration.
 *
 * Subprojects are not allowed to declare repositories of their own. Each third-party repository is
 * restricted to the groups it hosts through exclusive content filters, so it can neither be queried
 * for nor substitute any other dependency. Everything else is resolved from Maven Central.
 */
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // Aikar Command Framework (ACF).
        exclusiveContent {
            forRepository { maven("https://repo.aikar.co/content/groups/aikar/") { name = "aikar" } }
            filter { includeGroup("co.aikar") }
        }
        // Paper and Velocity APIs, plus Brigadier and BungeeCord Chat required by the Paper API.
        exclusiveContent {
            forRepository { maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" } }
            filter {
                includeGroupAndSubgroups("io.papermc")
                includeGroup("com.velocitypowered")
                includeGroup("com.mojang")
                includeGroup("net.md-5")
            }
        }
        // PacketEvents.
        exclusiveContent {
            forRepository { maven("https://repo.codemc.io/repository/maven-releases/") { name = "codemc" } }
            filter { includeGroup("com.github.retrooper") }
        }
        // Protocolize.
        exclusiveContent {
            forRepository {
                maven("https://mvn.exceptionflug.de/repository/exceptionflug-public/") { name = "exceptionflug" }
            }
            filter { includeGroup("dev.simplix") }
        }
        // Floodgate and its GeyserMC dependencies.
        exclusiveContent {
            forRepositories(
                maven("https://repo.opencollab.dev/maven-releases") { name = "opencollab" },
                maven("https://repo.opencollab.dev/maven-snapshots") { name = "opencollabSnapshots" },
            )
            filter { includeGroupAndSubgroups("org.geysermc") }
        }
        mavenCentral()
    }
}

rootProject.name = "LibreLoginNext"

include("Plugin", "API", "Adventure", "Lettuce")

// Third-party libraries that are relocated in a dedicated module before being bundled.
project(":Adventure").projectDir = file("Libraries/Adventure")
project(":Lettuce").projectDir = file("Libraries/Lettuce")
