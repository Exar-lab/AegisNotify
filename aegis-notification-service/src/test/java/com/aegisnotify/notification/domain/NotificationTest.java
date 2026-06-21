package com.aegisnotify.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
