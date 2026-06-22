package com.aegisnotify.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.InvalidRecipientException;
import com.aegisnotify.notification.domain.model.Notification;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTest {

  @Test
  void create_withValidEmail_setsStatusPending() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of("name", "John"), Priority.HIGH
    );

    assertNotNull(notification.getId());
    assertEquals(NotificationStatus.PENDING, notification.getStatus());
    assertEquals(Channel.EMAIL, notification.getChannel());
  }

  @Test
  void create_withValidSmsRecipient_succeeds() {
    Notification notification = Notification.create(
        Channel.SMS, "+34600000000", "sms-template",
        Map.of(), Priority.MEDIUM
    );

    assertNotNull(notification.getId());
    assertEquals(Channel.SMS, notification.getChannel());
    assertEquals("+34600000000", notification.getRecipient());
  }

  @Test
  void create_withValidWhatsappRecipient_succeeds() {
    Notification notification = Notification.create(
        Channel.WHATSAPP, "+5491112345678", "whatsapp-template",
        Map.of(), Priority.LOW
    );

    assertNotNull(notification.getId());
    assertEquals(Channel.WHATSAPP, notification.getChannel());
    assertEquals("+5491112345678", notification.getRecipient());
  }

  @Test
  void create_withValidPushToken_succeeds() {
    Notification notification = Notification.create(
        Channel.PUSH, "device-token-123", "push-template",
        Map.of(), Priority.HIGH
    );

    assertNotNull(notification.getId());
    assertEquals(Channel.PUSH, notification.getChannel());
    assertEquals("device-token-123", notification.getRecipient());
  }

  @Test
  void create_withInvalidEmail_throwsInvalidRecipientException() {
    assertThrows(InvalidRecipientException.class, () ->
        Notification.create(
            Channel.EMAIL, "not-an-email", "welcome",
            Map.of(), Priority.HIGH
        )
    );
  }

  @Test
  void create_withInvalidSmsRecipient_throwsInvalidRecipientException() {
    assertThrows(InvalidRecipientException.class, () ->
        Notification.create(
            Channel.SMS, "12345", "sms-template",
            Map.of(), Priority.MEDIUM
        )
    );
  }

  @Test
  void create_withBlankPushToken_throwsInvalidRecipientException() {
    assertThrows(InvalidRecipientException.class, () ->
        Notification.create(
            Channel.PUSH, " ", "push-template",
            Map.of(), Priority.HIGH
        )
    );
  }

  @Test
  void create_parametersAreDefensivelyCopied() {
    Map<String, Object> originalParams = new HashMap<>();
    originalParams.put("key", "value");

    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome",
        originalParams, Priority.HIGH
    );

    originalParams.put("extra", "should-not-appear");

    assertEquals(1, notification.getParameters().size());
    assertEquals("value", notification.getParameters().get("key"));
  }

  @Test
  void markProcessing_changesStatusToProcessing() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH
    );

    Notification processing = notification.markProcessing();

    assertEquals(NotificationStatus.PROCESSING, processing.getStatus());
    assertEquals(notification.getId(), processing.getId());
  }

  @Test
  void markSent_setsStatusAndProvider() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH
    );

    Notification sent = notification.markSent("SendGrid");

    assertEquals(NotificationStatus.SENT, sent.getStatus());
    assertEquals("SendGrid", sent.getProviderUsed());
    assertNull(sent.getErrorDetail());
  }

  @Test
  void markSentViaFallback_setsStatusAndFallbackProvider() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH
    );

    Notification sent = notification.markSentViaFallback("Mailgun");

    assertEquals(NotificationStatus.SENT_VIA_FALLBACK, sent.getStatus());
    assertEquals("Mailgun", sent.getProviderUsed());
  }

  @Test
  void markFailed_setsStatusAndErrorDetail() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH
    );

    Notification failed = notification.markFailed("Connection timeout");

    assertEquals(NotificationStatus.FAILED, failed.getStatus());
    assertEquals("Connection timeout", failed.getErrorDetail());
  }

  @Test
  void markFailedCritical_setsStatusAndErrorDetail() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH
    );

    Notification failed = notification.markFailedCritical("All providers exhausted");

    assertEquals(NotificationStatus.FAILED_CRITICAL, failed.getStatus());
    assertEquals("All providers exhausted", failed.getErrorDetail());
  }

  @Test
  void markCancelled_setsStatusToCancelled() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH
    );

    Notification cancelled = notification.markCancelled();

    assertEquals(NotificationStatus.CANCELLED, cancelled.getStatus());
  }

  @Test
  void resetToPending_clearsProviderAndError() {
    Notification notification = Notification.reconstitute(
        java.util.UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.FAILED,
        "SendGrid", "Timeout", java.time.Instant.now(), java.time.Instant.now()
    );

    Notification reset = notification.resetToPending();

    assertEquals(NotificationStatus.PENDING, reset.getStatus());
    assertNull(reset.getProviderUsed());
    assertNull(reset.getErrorDetail());
  }

  @Test
  void canCancel_pendingAndQueued_returnsTrue() {
    Notification pending = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH
    );

    assertTrue(pending.canCancel());

    Notification queued = Notification.reconstitute(
        java.util.UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.QUEUED,
        null, null, java.time.Instant.now(), java.time.Instant.now()
    );

    assertTrue(queued.canCancel());
  }

  @Test
  void canCancel_processingOrSent_returnsFalse() {
    Notification processing = Notification.reconstitute(
        java.util.UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.PROCESSING,
        null, null, java.time.Instant.now(), java.time.Instant.now()
    );

    assertFalse(processing.canCancel());
  }

  @Test
  void canRetry_failedOnly_returnsTrue() {
    Notification failed = Notification.reconstitute(
        java.util.UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.FAILED,
        null, "Error", java.time.Instant.now(), java.time.Instant.now()
    );

    assertTrue(failed.canRetry());
  }

  @Test
  void canRetry_failedCritical_returnsFalse() {
    Notification failedCritical = Notification.reconstitute(
        java.util.UUID.randomUUID(), Channel.EMAIL, "user@example.com", "welcome",
        Map.of(), Priority.HIGH, NotificationStatus.FAILED_CRITICAL,
        null, "Error", java.time.Instant.now(), java.time.Instant.now()
    );

    assertFalse(failedCritical.canRetry());
  }

  @Test
  void getParameters_returnsUnmodifiableMap() {
    Notification notification = Notification.create(
        Channel.EMAIL, "user@example.com", "welcome",
        Map.of("key", "value"), Priority.HIGH
    );

    assertThrows(UnsupportedOperationException.class, () ->
        notification.getParameters().put("new", "entry")
    );
  }
}
