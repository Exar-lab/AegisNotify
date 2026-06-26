package com.aegisnotify.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTest {

  @MockBean
  ReactiveJwtDecoder jwtDecoder;

  @Test
  void contextLoads() {
  }
}
