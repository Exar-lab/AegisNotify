package com.aegisnotify.notification;

import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.infrastructure.persistence.adapter.NotificationLogRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.NotificationRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.OutboxEventRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.TemplateRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceContextSmokeTest {

  // Context-only smoke test: adapter behavior is covered by dedicated tests.

  @MockitoBean
  private TemplateRenderer templateRenderer;

  @MockitoBean
  private TemplateRepositoryAdapter templateRepositoryAdapter;

  @MockitoBean
  private NotificationRepositoryAdapter notificationRepositoryAdapter;

  @MockitoBean
  private NotificationLogRepositoryAdapter notificationLogRepositoryAdapter;

  @MockitoBean
  private OutboxEventRepositoryAdapter outboxEventRepositoryAdapter;

  @MockitoBean
  private NotificationProviderPort notificationProviderPort;

  @MockitoBean
  private MessageBrokerPort messageBrokerPort;

  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  @Test
  void contextLoads_withoutExternalInfrastructure() {
  }
}
