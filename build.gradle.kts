plugins {
    base
    id("org.springframework.boot") version "4.1.2" apply false
}

allprojects {
    group = "com.orderflow"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

val serviceProjects = subprojects.filter { it.path.startsWith(":services:") }

configure(serviceProjects) {
    apply(plugin = "java-library")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "jacoco")

    dependencies {
        add("implementation", platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
        add("implementation", "org.springframework.boot:spring-boot-starter-webmvc")
        add("implementation", "org.springframework.boot:spring-boot-starter-data-jpa")
        add("implementation", "org.springframework.boot:spring-boot-starter-security")
        add("implementation", "org.springframework.boot:spring-boot-starter-validation")
        add("implementation", "org.springframework.boot:spring-boot-starter-actuator")
        add("runtimeOnly", "io.micrometer:micrometer-registry-prometheus")
        add("implementation", "org.springframework.boot:spring-boot-starter-flyway")
        add("implementation", "org.flywaydb:flyway-database-postgresql")
        add("implementation", "org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")

        add("runtimeOnly", "org.postgresql:postgresql")

        add("testImplementation", platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
        add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
        add("testImplementation", "org.mockito:mockito-junit-jupiter")
        add("testImplementation", "com.tngtech.archunit:archunit:1.5.1")
        add("testImplementation", "com.networknt:json-schema-validator:3.0.9")
        add("testImplementation", "org.testcontainers:testcontainers-junit-jupiter")
        add("testImplementation", "org.testcontainers:testcontainers-postgresql")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    extensions.configure<org.gradle.api.tasks.SourceSetContainer> {
        named("test") {
            java.srcDir(rootProject.file("quality/architecture-tests/src/test/java"))
            resources.srcDir(rootProject.file("contracts"))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty(
            "orderflow.architecture.root-package",
            "com.orderflow.${project.name.removeSuffix("-service")}"
        )
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<org.gradle.testing.jacoco.tasks.JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    rootProject.tasks.named("build") {
        dependsOn(tasks.named("build"))
    }
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Запускает тесты сервисов, архитектурные правила и проверки удалённых контрактов."
    dependsOn(serviceProjects.map { "${it.path}:test" })
    dependsOn(":quality:contract-tests:test")
    dependsOn(":tools:dlq-replay:test")
    dependsOn("verifyServiceIsolation")
}

tasks.register("verifyServiceIsolation") {
    group = "verification"
    description = "Запрещает Gradle project-зависимости из одного микросервиса в другой модуль репозитория."
    doLast {
        val violations = serviceProjects.flatMap { service ->
            service.configurations.flatMap { configuration ->
                configuration.dependencies
                    .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    .map { dependency ->
                        "${service.path}:${configuration.name} -> project ${dependency.name}"
                    }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Микросервисы не должны иметь project-зависимости:\n${violations.joinToString("\n")}"
            )
        }
    }
}

tasks.named("build") {
    dependsOn(":quality:contract-tests:build")
    dependsOn(":tools:dlq-replay:build")
    dependsOn("verifyServiceIsolation")
}
