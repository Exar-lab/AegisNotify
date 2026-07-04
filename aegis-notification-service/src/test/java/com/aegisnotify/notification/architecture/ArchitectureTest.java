package com.aegisnotify.notification.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.aegisnotify.notification",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_shouldNotDependOnApplication =
      noClasses()
          .that().resideInAPackage("..domain..")
          .should().dependOnClassesThat()
          .resideInAnyPackage("..application..", "..infrastructure..");

  @ArchTest
  static final ArchRule application_shouldNotDependOnInfrastructure =
      noClasses()
          .that().resideInAPackage("..application..")
          .should().dependOnClassesThat()
          .resideInAPackage("..infrastructure..");

  @ArchTest
  static final ArchRule domain_shouldNotUseSpringAnnotations =
      noClasses()
          .that().resideInAnyPackage("..domain..", "..application.port..")
          .should().dependOnClassesThat()
          .resideInAPackage("org.springframework..");

  @ArchTest
  static final ArchRule application_ports_shouldBeInterfaces =
      classes()
          .that().resideInAPackage("..application.port..")
          .should().beInterfaces();

  @ArchTest
  static final ArchRule controllers_shouldResideInWebPackage =
      classes()
          .that().areAnnotatedWith(RestController.class)
          .should().resideInAPackage("..infrastructure.web..");

  @ArchTest
  static final ArchRule adapters_shouldImplementOutputPorts =
      classes()
          .that().implement(resideInAPackage("..application.port.out.."))
          .should().resideInAPackage("..infrastructure..");
}
