plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":support:database-support"))
    implementation(project(":support:workflow-support"))
    implementation(project(":components:listings-data"))
    implementation(project(":components:profile"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation(testFixtures(project(":support:database-support")))
}
