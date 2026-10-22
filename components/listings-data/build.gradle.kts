dependencies {
    implementation(project(":support:database-support"))
    implementation("org.postgresql:postgresql:42.7.3")

    testImplementation(testFixtures(project(":support:database-support")))
}
