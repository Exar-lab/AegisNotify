package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.in.ProcessNotificationUseCase;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.application.service.NotificationProcessingTransactions.PreparedNotification;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orchestrates notification processing without holding a database
 * transaction across the provider call. The two short transactions live in
 * {@link NotificationProcessingTransactions}; the blocking HTTP call to the
 * channel provider runs here, between them, with no transaction open — so a
 * slow or hung provider never holds a DB connection or row lock.
 */
@Service
public class ProcessNotificationService implements ProcessNotificationUseCase {

  private final NotificationProviderPort notificationProviderPort;
  private final NotificationProcessingTransactions transactions;

  public ProcessNotificationService(NotificationProviderPort notificationProviderPort,
      NotificationProcessingTransactions transactions) {
    this.notificationProviderPort = notificationProviderPort;
    this.transactions = transactions;
  }

  @Override
  public NotificationResponse process(UUID notificationId) {
    PreparedNotification prepared = transactions.prepare(notificationId);

    ProviderResult result = notificationProviderPort.send(
        prepared.notification().getChannel(), prepared.notification().getRecipient(),
        prepared.renderedBody(), prepared.subject()
    );

    return transactions.applyResult(prepared.notification(), result);
  }
}
