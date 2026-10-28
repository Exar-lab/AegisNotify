package com.aegisnotify.audit.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class ApiDocsConfigTest {

  private static Object property(String file, String key) throws IOException {
    List<PropertySource<?>> sources = new YamlPropertySourceLoader()
        .load(file, new FileSystemResource("src/main/resources/" + file));
    return sources.stream().map(s -> s.getProperty(key))
        .filter(v -> v != null).findFirst().orElse(null);
  }

  @Test
  void openApi_exposesServiceTitleAndVersion() {
    OpenAPI openApi = new ApiDocsConfig().openApi();

    assertThat(openApi.getInfo().getTitle()).isEqualTo("AegisNotify Audit Service API");
    assertThat(openApi.getInfo().getVersion()).isNotBlank();
  }

  @Test
  void docs_areDisabledByDefault() throws IOException {
    assertThat(property("application.yml", "springdoc.api-docs.enabled")).isEqualTo(false);
    assertThat(property("application.yml", "springdoc.swagger-ui.enabled")).isEqualTo(false);
  }

  @Test
  void docs_areEnabledInLocalProfile() throws IOException {
    assertThat(property("application-local.yml", "springdoc.api-docs.enabled")).isEqualTo(true);
    assertThat(property("application-local.yml", "springdoc.swagger-ui.enabled")).isEqualTo(true);
    assertThat(property("application-local.yml", "server.forward-headers-strategy"))
        .isEqualTo("framework");
  }
}
