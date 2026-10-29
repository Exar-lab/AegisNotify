package com.aegisnotify.docs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Shared API documentation (OpenAPI / Swagger UI) support for the servlet services, active only
 * under the {@code local} profile. The docs paths are public there; CSRF protection stays enabled
 * and every other path keeps the protection of the service's own security configuration.
 */
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@Profile("local")
@ConditionalOnWebApplication(type = Type.SERVLET)
public class ApiDocsAutoConfiguration {

  @Bean
  @Order(1)
  SecurityFilterChain apiDocsSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
    return http.build();
  }

  @Bean
  OpenAPI openApi(@Value("${aegis.api-docs.title:AegisNotify API}") String title) {
    return new OpenAPI().info(new Info().title(title).version("v1"));
  }
}
