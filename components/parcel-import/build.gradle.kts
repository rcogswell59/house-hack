plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}

val ktorVersion: String by project

dependencies {
    implementation(project(":support:database-support"))
    implementation(project(":support:workflow-support"))
    implementation(project(":components:listings-data"))
    implementation(project(":components:profile"))
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation(testFixtures(project(":support:database-support")))
}
