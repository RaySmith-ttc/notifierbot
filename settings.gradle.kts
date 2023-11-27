rootProject.name = "notifierbot"

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven { setUrl("https://plugins.gradle.org/m2/") }
    }
}

fun RepositoryHandler.mavenRaySmith(name: String) {
    maven {
        url = uri("https://maven.pkg.github.com/raysmith-ttc/$name")
        credentials {
            username = System.getenv("GIT_USERNAME")
            password = System.getenv("GIT_TOKEN_READ")
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()

        mavenRaySmith("utils")
        mavenRaySmith("tg-bot")
        mavenRaySmith("exposed-option")

        flatDir {
            dirs("$rootDir/libs")
        }
    }

    versionCatalogs {
        val local = File("${rootProject.projectDir}/gradle/libs.versions.toml")
        if (!local.exists()) {
            create("libs") {
                from(files("${gradle.gradleUserHomeDir}/libs.versions.toml"))
            }
        }
    }
}