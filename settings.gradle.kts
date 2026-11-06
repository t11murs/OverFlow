plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "orderflow"

include(
    "services:identity-service",
    "services:catalog-service",
    "services:inventory-service",
    "services:order-service",
    "services:payment-service",
    "services:notification-service",
    "tools:dlq-replay",
    "quality:contract-tests",
)
