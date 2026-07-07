package com.aegisnotify.notification.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.notification.application.dto.CreateNotificationCommand;
import com.aegisnotify.notification.application.dto.NotificationLogEntry;
import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.dto.NotificationStatusResponse;
import com.aegisnotify.notification.application.port.in.CreateNotificationUseCase;
import com.aegisnotify.notification.application.port.in.GetNotificationStatusUseCase;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.LogStatus;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.NotificationNotFoundException;
import com.aegisnotify.notification.infrastructure.config.SecurityConfig;
import com.aegisnotify.notification.infrastructure.web.mapper.NotificationWebMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private CreateNotificationUseCase createNotificationUseCase;

  @MockitoBean
  private GetNotificationStatusUseCase getNotificationStatusUseCase;

  @MockitoBean
  private NotificationWebMapper mapper;

  @Test
  void postNotification_validRequest_returns202() throws Exception {
    UUID notificationId = UUID.randomUUID();
    CreateNotificationCommand command = new CreateNotificationCommand(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH
    );
    when(mapper.toCommand(any())).thenReturn(command);
    when(createNotificationUseCase.create(command))
        .thenReturn(new NotificationResponse(notificationId, NotificationStatus.PENDING));

    Map<String, Object> request = Map.of(
        "channel", "EMAIL",
        "recipient", "user@example.com",
        "templateName", "welcome",
        "parameters", Map.of("name", "John"),
        "priority", "HIGH"
    );

    mockMvc.perform(post("/api/v1/notifications")
            .with(jwt().authorities(() -> "SCOPE_notification:write"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").value(notificationId.toString()))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(header().exists("Location"));
  }

  @Test
  void postNotification_noToken_returns401() throws Exception {
    Map<String, Object> request = Map.of(
        "channel", "EMAIL",
        "recipient", "user@example.com",
        "templateName", "welcome",
        "parameters", Map.of("name", "John"),
        "priority", "HIGH"
    );

    mockMvc.perform(post("/api/v1/notifications")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void postNotification_missingRequiredScope_returns403() throws Exception {
    Map<String, Object> request = Map.of(
        "channel", "EMAIL",
        "recipient", "user@example.com",
        "templateName", "welcome",
        "parameters", Map.of("name", "John"),
        "priority", "HIGH"
    );

    mockMvc.perform(post("/api/v1/notifications")
            .with(jwt().authorities(() -> "SCOPE_notification:read"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void postNotification_missingChannel_returns400() throws Exception {
    Map<String, Object> request = Map.of(
        "recipient", "user@example.com",
        "templateName", "welcome"
    );

    mockMvc.perform(post("/api/v1/notifications")
            .with(jwt().authorities(() -> "SCOPE_notification:write"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postNotification_blankRecipient_returns400() throws Exception {
    Map<String, Object> request = Map.of(
        "channel", "EMAIL",
        "recipient", " ",
        "templateName", "welcome"
    );

    mockMvc.perform(post("/api/v1/notifications")
            .with(jwt().authorities(() -> "SCOPE_notification:write"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getStatus_found_returns200() throws Exception {
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();

    NotificationStatusResponse statusResponse = new NotificationStatusResponse(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        NotificationStatus.PENDING, now, now,
        List.of(new NotificationLogEntry(LogStatus.PENDING, "Accepted", now))
    );
    when(getNotificationStatusUseCase.getStatus(notificationId))
        .thenReturn(statusResponse);

    mockMvc.perform(get("/api/v1/notifications/{id}/status", notificationId)
            .with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(notificationId.toString()))
        .andExpect(jsonPath("$.channel").value("EMAIL"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.auditTrail").isArray())
        .andExpect(jsonPath("$.auditTrail[0].status").value("PENDING"));
  }

  @Test
  void getStatus_notFound_returns404() throws Exception {
    UUID notificationId = UUID.randomUUID();
    when(getNotificationStatusUseCase.getStatus(notificationId))
        .thenThrow(new NotificationNotFoundException(notificationId));

    mockMvc.perform(get("/api/v1/notifications/{id}/status", notificationId)
            .with(jwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void getStatus_noToken_returns401() throws Exception {
    UUID notificationId = UUID.randomUUID();

    mockMvc.perform(get("/api/v1/notifications/{id}/status", notificationId))
        .andExpect(status().isUnauthorized());
  }
}
