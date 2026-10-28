package com.aegisnotify.audit.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API documentation (OpenAPI / Swagger UI) support, active only under the
 * {@code local} profile. The docs paths are public there; without the
 * profile this class is not loaded and the paths keep the default protection
 * of {@link SecurityConfig}.
 */
@Configuration
@Profile("local")
public class ApiDocsConfig {

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
  OpenAPI openApi() {
    return new OpenAPI().info(new Info()
        .title("AegisNotify Audit Service API")
        .version("v1"));
  }
}
