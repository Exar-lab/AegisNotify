package com.aegisnotify.config.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * aegis-config-server is a thin module (just the application class and a security config)
 * with no hexagonal layering, so a full package-layout rule would be meaningless. The
 * no-field-injection rule is the one that is actually meaningful today and documents the
 * policy going forward as the module grows.
 */
@AnalyzeClasses(packages = "com.aegisnotify.config",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule noFields_shouldBeAnnotatedWithAutowired =
      noFields()
          .should().beAnnotatedWith(Autowired.class)
          .because("constructor injection should be used instead of field injection")
          .allowEmptyShould(true);
}
