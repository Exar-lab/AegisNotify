package com.aegisnotify.eureka.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * aegis-eureka-server is a single-class module (EurekaServerApplication) with no layering
 * to enforce. Per the issue's own guidance, the initial ArchUnit rule set here is minimal:
 * no-field-injection is close to the only rule that is actually meaningful right now, and it
 * documents the policy going forward as the module grows.
 */
@AnalyzeClasses(packages = "com.aegisnotify.eureka",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule noFields_shouldBeAnnotatedWithAutowired =
      noFields()
          .should().beAnnotatedWith(Autowired.class)
          .because("constructor injection should be used instead of field injection")
          .allowEmptyShould(true);
}
