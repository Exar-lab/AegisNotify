package com.aegisnotify.gateway.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * aegis-api-gateway is a thin module with no hexagonal (domain/application/infrastructure)
 * layering, so these rules are deliberately minimal: they document the policy going forward
 * (constructor injection, config/security classes staying in their existing packages) rather
 * than enforcing a layered architecture that does not apply here.
 */
@AnalyzeClasses(packages = "com.aegisnotify.gateway",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule noFields_shouldBeAnnotatedWithAutowired =
      noFields()
          .should().beAnnotatedWith(Autowired.class)
          .because("constructor injection should be used instead of field injection")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule configClasses_shouldResideInConfigPackage =
      classes()
          .that().haveSimpleNameEndingWith("Config")
          .or().haveSimpleNameEndingWith("Configuration")
          .should().resideInAPackage("..config..");

  @ArchTest
  static final ArchRule securityScopeClasses_shouldResideInSecurityPackage =
      classes()
          .that().haveSimpleNameEndingWith("Scopes")
          .should().resideInAPackage("..security..");
}
