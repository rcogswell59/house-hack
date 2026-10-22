plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}

val ktorVersion: String by project

dependencies {
    api(project(":support:database-support"))
    api(project(":support:workflow-support"))
    api(project(":components:profile"))
    api(project(":components:listings-data"))
    api(project(":components:rentcast-client"))
    api(project(":components:parcel-import"))
    api(project(":components:data-collector"))
    api(project(":components:data-analyzer"))

    api("io.ktor:ktor-client-core-jvm:$ktorVersion")   // HttpRentCastClient/FloridaCadastralClient take an engine parameter
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
