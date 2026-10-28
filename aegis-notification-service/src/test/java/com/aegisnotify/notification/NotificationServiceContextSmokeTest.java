package com.aegisnotify.notification;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.infrastructure.persistence.adapter.AggregationBufferRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.DeliveryAttemptRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.NotificationLogRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.NotificationRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.OutboxEventRepositoryAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.ProviderCallMetricsAdapter;
import com.aegisnotify.notification.infrastructure.persistence.adapter.TemplateRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceContextSmokeTest {

  // Context-only smoke test: adapter behavior is covered by dedicated tests.
  // TemplateRenderer is intentionally NOT mocked here — the real
  // MustacheTemplateRenderer bean must boot successfully (issue #26).

  @MockitoBean
  private TemplateRepositoryAdapter templateRepositoryAdapter;

  @MockitoBean
  private NotificationRepositoryAdapter notificationRepositoryAdapter;

  @MockitoBean
  private NotificationLogRepositoryAdapter notificationLogRepositoryAdapter;

  @MockitoBean
  private DeliveryAttemptRepositoryAdapter deliveryAttemptRepositoryAdapter;

  @MockitoBean
  private OutboxEventRepositoryAdapter outboxEventRepositoryAdapter;

  @MockitoBean
  private AggregationBufferRepositoryAdapter aggregationBufferRepositoryAdapter;

  @MockitoBean
  private ProviderCallMetricsAdapter providerCallMetricsAdapter;

  @MockitoBean
  private NotificationProviderPort notificationProviderPort;

  @MockitoBean
  private MessageBrokerPort messageBrokerPort;

  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  void contextLoads_withoutExternalInfrastructure() {
    assertNotNull(applicationContext);
  }
}
