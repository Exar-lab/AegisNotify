package com.aegisnotify.audit.infrastructure.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.audit.application.port.in.GetAuditTrailUseCase;
import com.aegisnotify.audit.application.port.in.SearchAuditEventsUseCase;
import com.aegisnotify.audit.infrastructure.web.AuditQueryController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies scope-based authorization declared by {@link SecurityConfig},
 * independent of {@code AuditQueryControllerTest}'s business-logic
 * assertions.
 */
@WebMvcTest(AuditQueryController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private GetAuditTrailUseCase getAuditTrailUseCase;

  @MockitoBean
  private SearchAuditEventsUseCase searchAuditEventsUseCase;

  @Test
  void auditTrail_withAuditReadScope_returns200() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(get("/api/v1/audit/{notificationId}", id)
            .with(jwt().authorities(() -> "SCOPE_audit:read")))
        .andExpect(status().isOk());
  }

  @Test
  void auditTrail_withoutAuditReadScope_returns403() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(get("/api/v1/audit/{notificationId}", id)
            .with(jwt().authorities(() -> "SCOPE_notification:read")))
        .andExpect(status().isForbidden());
  }

  @Test
  void auditSearch_withoutAuditReadScope_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/audit")
            .with(jwt().authorities(() -> "SCOPE_notification:read")))
        .andExpect(status().isForbidden());
  }

  @Test
  void auditTrail_malformedToken_returns401NotForbidden() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(get("/api/v1/audit/{notificationId}", id)
            .header("Authorization", "Bearer not-a-valid-jwt"))
        .andExpect(status().isUnauthorized());
  }
}
