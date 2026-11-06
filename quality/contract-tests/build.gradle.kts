plugins {
    java
}

description = "Автоматические проверки удалённых контрактов OrderFlow"

dependencies {
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.networknt:json-schema-validator:3.0.8")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("contracts"))
    }
}

tasks.test {
    useJUnitPlatform()
}
