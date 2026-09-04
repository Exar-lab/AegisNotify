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
  private final UUID aggregationId;
  private final String aggregateBody;

  private Notification(UUID id, Channel channel, String recipient,
      String templateName, Map<String, Object> parameters, Priority priority,
      NotificationStatus status, String providerUsed, String errorDetail,
      Instant createdAt, Instant updatedAt, UUID aggregationId, String aggregateBody) {
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
    this.aggregationId = aggregationId;
    this.aggregateBody = aggregateBody;
  }

  public static Notification create(Channel channel, String recipient,
      String templateName, Map<String, Object> parameters, Priority priority) {
    validateRecipient(channel, recipient);
    Instant now = Instant.now();
    return new Notification(
        UUID.randomUUID(), channel, recipient, templateName,
        Map.copyOf(parameters), priority, NotificationStatus.PENDING,
        null, null, now, now, null, null
    );
  }

  /**
   * Back-compat overload preserving the pre-aggregation 11-argument shape.
   * {@code aggregationId} and {@code aggregateBody} default to {@code null}
   * (i.e. "not part of an aggregate"). Existing callers are unaffected by the
   * aggregation feature (issue #86).
   */
  public static Notification reconstitute(UUID id, Channel channel,
      String recipient, String templateName, Map<String, Object> parameters,
      Priority priority, NotificationStatus status, String providerUsed,
      String errorDetail, Instant createdAt, Instant updatedAt) {
    return reconstitute(id, channel, recipient, templateName, parameters, priority,
        status, providerUsed, errorDetail, createdAt, updatedAt, null, null);
  }

  public static Notification reconstitute(UUID id, Channel channel,
      String recipient, String templateName, Map<String, Object> parameters,
      Priority priority, NotificationStatus status, String providerUsed,
      String errorDetail, Instant createdAt, Instant updatedAt,
      UUID aggregationId, String aggregateBody) {
    return new Notification(
        id, channel, recipient, templateName,
        Map.copyOf(parameters), priority, status, providerUsed,
        errorDetail, createdAt, updatedAt, aggregationId, aggregateBody
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
        createdAt, Instant.now(), aggregationId, aggregateBody);
  }

  public Notification markProcessing() {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.PROCESSING, providerUsed, errorDetail,
        createdAt, Instant.now(), aggregationId, aggregateBody);
  }

  public Notification markSent(String provider) {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.SENT, provider, null,
        createdAt, Instant.now(), aggregationId, aggregateBody);
  }

  public Notification markSentViaFallback(String provider) {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.SENT_VIA_FALLBACK, provider, null,
        createdAt, Instant.now(), aggregationId, aggregateBody);
  }

  public Notification markFailed(String error) {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.FAILED, providerUsed, error,
        createdAt, Instant.now(), aggregationId, aggregateBody);
  }

  public Notification markFailedCritical(String error) {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.FAILED_CRITICAL, providerUsed, error,
        createdAt, Instant.now(), aggregationId, aggregateBody);
  }

  public Notification markCancelled() {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.CANCELLED, providerUsed, errorDetail,
        createdAt, Instant.now(), aggregationId, aggregateBody);
  }

  public Notification resetToPending() {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, NotificationStatus.PENDING, null, null,
        createdAt, Instant.now(), aggregationId, aggregateBody);
  }

  /**
   * Marks this notification as folded into an aggregate send (issue #86,
   * Slice 2/3 forward-looking; the DB columns and this method exist from
   * Slice 1 onward since Slice 1 owns the migration). {@code aggregateBody}
   * is non-null only on the leader notification of a group (X2) — sibling
   * notifications are linked via {@code aggregationId} alone.
   *
   * <p>This method deliberately leaves {@code status} untouched. The leader
   * gets its status advanced separately, the normal way, once its single
   * outbox event is published ({@code
   * PublishOutboxEventTransactions#publishOne}). Callers resolving a
   * <em>sibling</em> (no outbox event of its own) must chain {@link
   * #markQueued()} onto the result — see {@code
   * AggregationFlushTransactions#flushAggregate} — otherwise the sibling
   * stays {@code PENDING} forever with no path to ever leave it.</p>
   */
  public Notification markAggregated(UUID aggregationId, String aggregateBody) {
    return reconstitute(id, channel, recipient, templateName, parameters,
        priority, status, providerUsed, errorDetail,
        createdAt, Instant.now(), aggregationId, aggregateBody);
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

  public UUID getAggregationId() {
    return aggregationId;
  }

  public String getAggregateBody() {
    return aggregateBody;
  }
}
