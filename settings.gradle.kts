rootProject.name = "kotlin-ktor-starter"

include(
    "applications:basic-server",
    "applications:data-analyzer-server",
    "applications:data-collector-server",

    "components:data-collector",
    "components:profile",
    "components:listings-data",
    "components:rentcast-client",
    "components:parcel-import",
    "components:wiring",
    "components:data-analyzer",

    "support:database-support",
    "support:logging-support",
    "support:workflow-support"
)
