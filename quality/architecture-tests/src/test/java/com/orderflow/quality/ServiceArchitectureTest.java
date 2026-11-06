package com.orderflow.quality;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ServiceArchitectureTest {

    private static final List<String> SERVICE_PACKAGES = List.of(
            "identity", "catalog", "inventory", "order", "payment", "notification");

    private static String rootPackage;
    private static JavaClasses classes;

    @BeforeAll
    static void importServiceClasses() {
        rootPackage = System.getProperty("orderflow.architecture.root-package");
        if (rootPackage == null || !rootPackage.matches("com\\.orderflow\\.[a-z]+")) {
            throw new IllegalStateException("Service root package is not configured");
        }
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(rootPackage);
    }

    @Test
    void domainDoesNotDependOnInfrastructureFrameworks() {
        noClasses()
                .that().resideInAPackage(rootPackage + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.apache.kafka..",
                        "tools.jackson..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void domainAndApplicationDoNotDependOnAdapters() {
        noClasses()
                .that().resideInAnyPackage(rootPackage + ".domain..", rootPackage + ".application..")
                .should().dependOnClassesThat().resideInAPackage(rootPackage + ".adapter..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void serviceDoesNotDependOnAnotherServicePackage() {
        String currentService = rootPackage.substring(rootPackage.lastIndexOf('.') + 1);
        String[] forbiddenPackages = SERVICE_PACKAGES.stream()
                .filter(service -> !service.equals(currentService))
                .map(service -> "com.orderflow." + service + "..")
                .toArray(String[]::new);

        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages)
                .check(classes);
    }
}
