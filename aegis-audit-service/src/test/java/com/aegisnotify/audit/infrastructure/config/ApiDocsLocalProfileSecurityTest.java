package com.aegisnotify.audit.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.audit.application.port.in.GetAuditTrailUseCase;
import com.aegisnotify.audit.application.port.in.SearchAuditEventsUseCase;
import com.aegisnotify.audit.infrastructure.web.AuditQueryController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With the {@code local} profile the API-docs paths are public; everything
 * else keeps its existing protection. The {@code @WebMvcTest} slice does not
 * load springdoc, so "reachable" means security let the request through
 * (anything but 401/403, e.g. a dispatcher 404).
 */
@WebMvcTest(AuditQueryController.class)
@Import({SecurityConfig.class, ApiDocsConfig.class})
@ActiveProfiles("local")
class ApiDocsLocalProfileSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private GetAuditTrailUseCase getAuditTrailUseCase;

  @MockitoBean
  private SearchAuditEventsUseCase searchAuditEventsUseCase;

  @ParameterizedTest
  @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs/swagger-config",
      "/swagger-ui/index.html", "/swagger-ui.html"})
  void docsPaths_withoutJwt_areNotRejectedBySecurity(String path) throws Exception {
    int httpStatus = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

    assertThat(httpStatus).isNotIn(401, 403);
  }

  @Test
  void businessEndpoint_withoutJwt_stillReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/audit/{notificationId}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void docsPath_postWithoutCsrfToken_isRejected() throws Exception {
    mockMvc.perform(post("/v3/api-docs")).andExpect(status().isForbidden());
  }
}
