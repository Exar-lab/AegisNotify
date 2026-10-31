package com.aegisnotify.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.SecurityFilterChain;

class ApiDocsAutoConfigurationTest {

  private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          WebMvcAutoConfiguration.class,
          SecurityAutoConfiguration.class,
          ApiDocsAutoConfiguration.class));

  @Test
  void localProfile_registersDocsChainAndOpenApi() {
    runner.withPropertyValues("spring.profiles.active=local").run(context -> {
      assertThat(context).hasSingleBean(OpenAPI.class);
      assertThat(context).hasBean("apiDocsSecurityFilterChain");
      assertThat(context).getBean("apiDocsSecurityFilterChain")
          .isInstanceOf(SecurityFilterChain.class);
    });
  }

  @Test
  void otherProfile_registersNothing() {
    runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
      assertThat(context).doesNotHaveBean(OpenAPI.class);
      assertThat(context).doesNotHaveBean("apiDocsSecurityFilterChain");
    });
  }

  @Test
  void noProfile_registersNothing() {
    runner.run(context -> {
      assertThat(context).doesNotHaveBean(OpenAPI.class);
      assertThat(context).doesNotHaveBean("apiDocsSecurityFilterChain");
    });
  }

  @Test
  void title_defaultsWhenPropertyMissing() {
    runner.withPropertyValues("spring.profiles.active=local").run(context -> {
      OpenAPI openApi = context.getBean(OpenAPI.class);
      assertThat(openApi.getInfo().getTitle()).isEqualTo("AegisNotify API");
      assertThat(openApi.getInfo().getVersion()).isEqualTo("v1");
    });
  }

  @Test
  void title_honorsProperty() {
    runner.withPropertyValues("spring.profiles.active=local",
        "aegis.api-docs.title=AegisNotify Audit Service API").run(context ->
        assertThat(context.getBean(OpenAPI.class).getInfo().getTitle())
            .isEqualTo("AegisNotify Audit Service API"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs/swagger-config",
      "/swagger-ui/index.html", "/swagger-ui.html"})
  void chain_matchesDocsPaths(String path) {
    runner.withPropertyValues("spring.profiles.active=local").run(context -> {
      SecurityFilterChain chain =
          context.getBean("apiDocsSecurityFilterChain", SecurityFilterChain.class);
      assertThat(chain.matches(request(path))).isTrue();
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/notifications", "/actuator/health"})
  void chain_doesNotMatchBusinessPaths(String path) {
    runner.withPropertyValues("spring.profiles.active=local").run(context -> {
      SecurityFilterChain chain =
          context.getBean("apiDocsSecurityFilterChain", SecurityFilterChain.class);
      assertThat(chain.matches(request(path))).isFalse();
    });
  }

  private static MockHttpServletRequest request(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setServletPath(path);
    return request;
  }
}
