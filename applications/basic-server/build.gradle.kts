plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.initialcapacity.househack.web"

val ktorVersion: String by project

dependencies {
    implementation(project(":components:wiring"))
    implementation(project(":support:logging-support"))

    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-freemarker-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    testImplementation(testFixtures(project(":support:database-support")))
}

tasks {
    assemble { dependsOn(shadowJar) }
    jar { archiveFileName.set("basic-server-thin.jar") }
    shadowJar {
        archiveFileName.set("basic-server.jar")
        mergeServiceFiles()
        manifest { attributes("Main-Class" to "io.initialcapacity.househack.web.AppKt") }
    }
}
