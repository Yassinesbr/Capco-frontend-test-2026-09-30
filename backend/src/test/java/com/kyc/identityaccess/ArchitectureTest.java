package com.kyc.identityaccess;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Protects the hexagonal dependency direction required by ADR-0005. */
@AnalyzeClasses(
        packages = "com.kyc.identityaccess",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnFrameworkOrAdapters = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "java.sql..", "..adapter..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..");
}
