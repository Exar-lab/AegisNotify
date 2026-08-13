package com.aegisnotify.notification.infrastructure.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.notification.application.port.in.CreateNotificationUseCase;
import com.aegisnotify.notification.application.port.in.GetNotificationStatusUseCase;
import com.aegisnotify.notification.infrastructure.web.NotificationController;
import com.aegisnotify.notification.infrastructure.web.mapper.NotificationWebMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies scope-based authorization declared by {@link SecurityConfig},
 * independent of {@code NotificationControllerTest}'s business-logic
 * assertions.
 */
@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CreateNotificationUseCase createNotificationUseCase;

  @MockitoBean
  private GetNotificationStatusUseCase getNotificationStatusUseCase;

  @MockitoBean
  private NotificationWebMapper mapper;

  @Test
  void statusEndpoint_withNotificationReadScope_returns200() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(get("/api/v1/notifications/{id}/status", id)
            .with(jwt().authorities(() -> "SCOPE_notification:read")))
        .andExpect(status().isOk());
  }

  @Test
  void statusEndpoint_withoutNotificationReadScope_returns403() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(get("/api/v1/notifications/{id}/status", id)
            .with(jwt().authorities(() -> "SCOPE_notification:write")))
        .andExpect(status().isForbidden());
  }

  @Test
  void submitEndpoint_withoutNotificationWriteScope_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/notifications")
            .with(jwt().authorities(() -> "SCOPE_notification:read"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void statusEndpoint_malformedToken_returns401NotForbidden() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(get("/api/v1/notifications/{id}/status", id)
            .header("Authorization", "Bearer not-a-valid-jwt"))
        .andExpect(status().isUnauthorized());
  }
}
