package com.aegisnotify.notification;

import com.aegisnotify.notification.application.port.out.DeadLetterQueuePort;
import com.aegisnotify.notification.application.port.out.MessageBrokerPort;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTest {

  @MockitoBean
  private TemplateRenderer templateRenderer;

  @MockitoBean
  private NotificationProviderPort notificationProviderPort;

  @MockitoBean
  private MessageBrokerPort messageBrokerPort;

  @MockitoBean
  private DeadLetterQueuePort deadLetterQueuePort;

  @Test
  void contextLoads() {
  }
}
