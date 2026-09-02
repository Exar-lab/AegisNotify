package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.port.out.OutboxEventRepository;
import com.aegisnotify.notification.application.service.PublishOutboxEventService;
import com.aegisnotify.notification.application.service.PublishOutboxEventTransactions;
import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers {@link PublishOutboxEventService}'s batch-loop behavior only. The
 * per-event publish/mark-processed/audit work now lives in
 * {@link PublishOutboxEventTransactions} (see
 * {@code PublishOutboxEventTransactionsTest}), each call bracketed by its own
 * transaction — these tests prove the loop itself never lets one event's
 * failure roll back or block another (issue #27 Slice 0a fix: duplicate
 * delivery on partial batch failure).
 */
@ExtendWith(MockitoExtension.class)
class PublishOutboxEventServiceTest {

  @Mock
  private OutboxEventRepository outboxEventRepository;

  @Mock
  private PublishOutboxEventTransactions transactions;

  private PublishOutboxEventService service;

  private PublishOutboxEventService newService() {
    return new PublishOutboxEventService(outboxEventRepository, transactions);
  }

  @Test
  void publishPending_withPendingEvents_delegatesEachEventToItsOwnTransaction() {
    UUID notificationId = UUID.randomUUID();
    OutboxEvent event = OutboxEvent.create(notificationId,
        Map.of("id", notificationId.toString(), "priority", "HIGH"));

    when(outboxEventRepository.findPendingEvents()).thenReturn(List.of(event));

    service = newService();
    int count = service.publishPending();

    assertEquals(1, count);
    verify(transactions).publishOne(event);
  }

  @Test
  void publishPending_noPendingEvents_returnsZero() {
    when(outboxEventRepository.findPendingEvents()).thenReturn(List.of());

    service = newService();
    int count = service.publishPending();

    assertEquals(0, count);
    verify(transactions, never()).publishOne(any());
  }

  @Test
  void publishPending_middleEventFails_doesNotRollBackOrBlockOtherEvents() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();

    OutboxEvent event1 = OutboxEvent.create(id1, Map.of("id", id1.toString(), "priority", "HIGH"));
    OutboxEvent event2 =
        OutboxEvent.create(id2, Map.of("id", id2.toString(), "priority", "MEDIUM"));
    OutboxEvent event3 = OutboxEvent.create(id3, Map.of("id", id3.toString(), "priority", "LOW"));

    when(outboxEventRepository.findPendingEvents())
        .thenReturn(List.of(event1, event2, event3));

    // Explicit passthrough stubs for events 1 and 3: once ANY stub exists for
    // publishOne(), Mockito's strict-stubs mode requires every invocation of that
    // method to match a stub, so these make the "no-op success" case explicit rather
    // than relying on default mock behavior.
    doNothing().when(transactions).publishOne(event1);
    doNothing().when(transactions).publishOne(event3);
    // event 2's own transaction fails independently of events 1 and 3.
    doThrow(new RuntimeException("broker unavailable"))
        .when(transactions).publishOne(event2);

    service = newService();
    int count = service.publishPending();

    // Only events 1 and 3 succeed (published exactly once each); event 2 stays
    // UNPROCESSED for retry without ever touching 1 or 3's already-committed state.
    assertEquals(2, count);

    InOrder inOrder = Mockito.inOrder(transactions);
    inOrder.verify(transactions).publishOne(event1);
    inOrder.verify(transactions).publishOne(event2);
    inOrder.verify(transactions).publishOne(event3);

    verify(transactions).publishOne(event1);
    verify(transactions).publishOne(event2);
    verify(transactions).publishOne(event3);
  }
}
