package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.application.service.NotificationProcessingTransactions;
import com.aegisnotify.notification.application.service.NotificationProcessingTransactions.PreparedNotification;
import com.aegisnotify.notification.application.service.ProcessNotificationService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.Notification;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verifies only the orchestration in {@link ProcessNotificationService}: it
 * must call the provider between {@code prepare} and {@code applyResult}
 * with no transaction of its own — the transactional behavior itself is
 * covered by {@link NotificationProcessingTransactionsTest}.
 */
@ExtendWith(MockitoExtension.class)
class ProcessNotificationServiceTest {

  @Mock
  private NotificationProviderPort notificationProviderPort;

  @Mock
  private NotificationProcessingTransactions transactions;

  private ProcessNotificationService service;

  @Test
  void process_delegatesToPrepareThenProviderThenApplyResult() {
    service = new ProcessNotificationService(notificationProviderPort, transactions);

    UUID notificationId = UUID.randomUUID();
    Notification processing = Notification.reconstitute(
        notificationId, Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH, NotificationStatus.PROCESSING,
        null, null, Instant.now(), Instant.now()
    );
    PreparedNotification prepared =
        new PreparedNotification(processing, "Welcome", "Hello John");
    ProviderResult providerResult =
        new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null);
    NotificationResponse expectedResponse =
        new NotificationResponse(notificationId, NotificationStatus.SENT);

    when(transactions.prepare(notificationId)).thenReturn(prepared);
    when(notificationProviderPort.send(Channel.EMAIL, "user@example.com", "Hello John", "Welcome"))
        .thenReturn(providerResult);
    when(transactions.applyResult(processing, providerResult)).thenReturn(expectedResponse);

    NotificationResponse response = service.process(notificationId);

    assertEquals(expectedResponse, response);
    verify(transactions).prepare(notificationId);
    verify(notificationProviderPort)
        .send(Channel.EMAIL, "user@example.com", "Hello John", "Welcome");
    verify(transactions).applyResult(processing, providerResult);
  }
}
