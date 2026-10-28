package com.aegisnotify.notification.infrastructure.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.notification.application.port.in.CancelNotificationUseCase;
import com.aegisnotify.notification.application.port.in.CreateNotificationUseCase;
import com.aegisnotify.notification.application.port.in.GetNotificationStatusUseCase;
import com.aegisnotify.notification.application.port.in.ListNotificationsUseCase;
import com.aegisnotify.notification.application.port.in.RetryFailedNotificationUseCase;
import com.aegisnotify.notification.infrastructure.web.NotificationController;
import com.aegisnotify.notification.infrastructure.web.mapper.NotificationWebMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Without the {@code local} profile the docs paths stay protected: the
 * profile-gated {@link ApiDocsConfig} must not contribute its permitAll chain.
 */
@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, ApiDocsConfig.class})
class ApiDocsDefaultProfileSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CreateNotificationUseCase createNotificationUseCase;

  @MockitoBean
  private GetNotificationStatusUseCase getNotificationStatusUseCase;

  @MockitoBean
  private CancelNotificationUseCase cancelNotificationUseCase;

  @MockitoBean
  private RetryFailedNotificationUseCase retryFailedNotificationUseCase;

  @MockitoBean
  private ListNotificationsUseCase listNotificationsUseCase;

  @MockitoBean
  private NotificationWebMapper mapper;

  @ParameterizedTest
  @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html"})
  void docsPaths_withoutJwt_return401(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
  }
}
