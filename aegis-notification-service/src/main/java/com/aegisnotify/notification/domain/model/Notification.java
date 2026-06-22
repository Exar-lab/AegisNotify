package com.aegisnotify.notification.domain.model;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.exception.InvalidRecipientException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Notification {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[\\w\\-\\.]+@([\\w\\-]+\\.)+[\\w\\-]{2,4}$");
  private static final Pattern PHONE_PATTERN =
      Pattern.compile("^\\+[1-9]\\d{1,14}$");

  private final UUID id;
  private final Channel channel;
  private final String recipient;
  private final String templateName;
  private final Map<String, Object> parameters;
  private final Priority priority;
  private final NotificationStatus status;
  private final String providerUsed;
  private final String errorDetail;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Notification(UUID id, Channel channel, String recipient,
      String templateName, Map<String, Object> parameters, Priority priority,
      NotificationStatus status, String providerUsed, String errorDetail,
      Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.channel = channel;
    this.recipient = recipient;
    this.templateName = templateName;
    this.parameters = parameters;
    this.priority = priority;
    this.status = status;
    this.providerUsed = providerUsed;
    this.errorDetail = errorDetail;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Notification create(Channel channel, String recipient,
      String templateName, Map<String, Object> parameters, Priority priority) {
    validateRecipient(channel, recipient);
    Instant now = Instant.now();
    return new Notification(
        UUID.randomUUID(), channel, recipient, templateName,
        Map.copyOf(parameters), priority, NotificationStatus.PENDING,
        null, null, now, now
    );
  }

  public static Notification reconstitute(UUID id, Channel channel,
      String recipient, String templateName, Map<String, Object> parameters,
      Priority priority, NotificationStatus status, String providerUsed,
      String errorDetail, Instant createdAt, Instant updatedAt) {
    return new Notification(
        id, channel, recipient, templateName,
        Map.copyOf(parameters), priority, status, providerUsed,
        errorDetail, createdAt, updatedAt
    );
  }

  private static void validateRecipient(Channel channel, String recipient) {
    switch (channel) {
      case EMAIL -> {
        if (!EMAIL_PATTERN.matcher(recipient).matches()) {
          throw new InvalidRecipientException(channel, recipient);
        }
      }
      case SMS, WHATSAPP -> {
        if (!PHONE_PATTERN.matcher(recipient).matches()) {
          throw new InvalidRecipientException(channel, recipient);
        }
      }
      case PUSH -> {
        if (recipient == null || recipient.isBlank()) {
          throw new InvalidRecipientException(channel, recipient);
        }
      }
      default -> throw new InvalidRecipientException(channel, recipient);
    }
  }

  public Notification markQueued() {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.QUEUED, providerUsed, errorDetail,
        createdAt, Instant.now());
  }

  public Notification markProcessing() {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.PROCESSING, providerUsed, errorDetail,
        createdAt, Instant.now());
  }

  public Notification markSent(String provider) {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.SENT, provider, null,
        createdAt, Instant.now());
  }

  public Notification markSentViaFallback(String provider) {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.SENT_VIA_FALLBACK, provider, null,
        createdAt, Instant.now());
  }

  public Notification markFailed(String error) {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.FAILED, providerUsed, error,
        createdAt, Instant.now());
  }

  public Notification markFailedCritical(String error) {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.FAILED_CRITICAL, providerUsed, error,
        createdAt, Instant.now());
  }

  public Notification markCancelled() {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.CANCELLED, providerUsed, errorDetail,
        createdAt, Instant.now());
  }

  public Notification resetToPending() {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.PENDING, null, null,
        createdAt, Instant.now());
  }

  public boolean canCancel() {
    return status == NotificationStatus.PENDING || status == NotificationStatus.QUEUED;
  }

  public boolean canRetry() {
    return status == NotificationStatus.FAILED;
  }

  public UUID getId() {
    return id;
  }

  public Channel getChannel() {
    return channel;
  }

  public String getRecipient() {
    return recipient;
  }

  public String getTemplateName() {
    return templateName;
  }

  public Map<String, Object> getParameters() {
    return Collections.unmodifiableMap(parameters);
  }

  public Priority getPriority() {
    return priority;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public String getProviderUsed() {
    return providerUsed;
  }

  public String getErrorDetail() {
    return errorDetail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
