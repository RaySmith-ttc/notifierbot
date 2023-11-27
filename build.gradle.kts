import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    kotlin("jvm") version "2.0.0-Beta1"
    application
    alias(libs.plugins.ktor)
    alias(libs.plugins.setupapp)
}

group = "ru.raysmith"
version = "1.0"

val env = System.getProperty("env")?.lowercase(Locale.getDefault())
    ?: System.getenv("env")?.lowercase(Locale.getDefault())
    ?: "dev"

application {
    mainClass.set("ru.raysmith.notifierbot.BotKt")
}

setupapp {

}

tasks {
    val copyRSDependenciesToLocalDir by registering(Copy::class) {
        group = "distribution"
        description = "Copy all dependencies of 'ru.raysmith' group to local directory"
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        subprojects.forEach { project ->
            from(project.configurations["compileClasspath"].asFileTree.filter { it.path.contains("ru.raysmith") })
        }
        into("/libs")
    }

    withType<Test> {
        useJUnitPlatform()
    }

    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
        }
    }
}

ktor {
    fatJar {
        archiveFileName.set("notifierbot-$version-$env.jar")
    }
}

dependencies {

    implementation(libs.bundles.database)
    implementation(libs.exposed.java.time)


    implementation(libs.raysmith.tgBot)
    implementation(libs.ktor.client.core)
    implementation(libs.raysmith.utils)
    implementation(libs.raysmith.exposedOption)

    implementation(libs.sentry)
    implementation(libs.logback)
    implementation(libs.timeshape)

    // Tests
    val jupiterVersion = "5.8.2"
    testImplementation("org.junit.jupiter:junit-jupiter:$jupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
    testImplementation("org.assertj:assertj-core:3.22.0")
}