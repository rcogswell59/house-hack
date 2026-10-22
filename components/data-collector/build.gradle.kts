dependencies {
    implementation(project(":support:database-support"))
    implementation(project(":support:workflow-support"))
    implementation(project(":components:listings-data"))
    implementation(project(":components:profile"))
    implementation(project(":components:rentcast-client"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    testImplementation(testFixtures(project(":support:database-support")))
}
