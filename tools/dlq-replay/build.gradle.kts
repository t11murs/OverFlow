plugins {
    application
}

description = "OrderFlow DLQ replay administration tool"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    implementation("org.apache.kafka:kafka-clients")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.orderflow.tools.dlq.DlqReplayApplication"
}

tasks.test {
    useJUnitPlatform()
}

rootProject.tasks.named("build") {
    dependsOn(tasks.named("build"))
}
