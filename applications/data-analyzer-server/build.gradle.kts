plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.initialcapacity.househack.analyzer"

val ktorVersion: String by project

dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.7")

    implementation(project(":components:wiring"))

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation(testFixtures(project(":support:database-support")))
}

tasks {
    assemble { dependsOn(shadowJar) }
    jar { archiveFileName.set("data-analyzer-server-thin.jar") }
    shadowJar {
        archiveFileName.set("data-analyzer-server.jar")
        mergeServiceFiles()
        manifest { attributes("Main-Class" to "io.initialcapacity.househack.analyzer.AppKt") }
    }
}
