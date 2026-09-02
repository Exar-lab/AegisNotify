package com.aegisnotify.notification.domain.model;

import com.aegisnotify.notification.domain.enums.AggregationBufferStatus;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import java.time.Instant;
import java.util.UUID;

/**
 * A single notification held in the aggregation buffer, durable in the new
 * {@code aggregation_buffer} table (B1 of the design). Never the sole
 * authoritative store for a delivered notification — the owning notification
 * row always exists in {@code notifications} for the lifetime of this
 * buffered row.
 */
public final class BufferedNotification {

  private final UUID id;
  private final UUID notificationId;
  private final Channel channel;
  private final String recipient;
  private final String templateName;
  private final Priority priority;
  private final AggregationBufferStatus status;
  private final Instant expiresAt;
  private final Instant claimedAt;
  private final int attempts;
  private final Instant createdAt;

  private BufferedNotification(UUID id, UUID notificationId, Channel channel,
      String recipient, String templateName, Priority priority,
      AggregationBufferStatus status, Instant expiresAt, Instant claimedAt,
      int attempts, Instant createdAt) {
    this.id = id;
    this.notificationId = notificationId;
    this.channel = channel;
    this.recipient = recipient;
    this.templateName = templateName;
    this.priority = priority;
    this.status = status;
    this.expiresAt = expiresAt;
    this.claimedAt = claimedAt;
    this.attempts = attempts;
    this.createdAt = createdAt;
  }

  public static BufferedNotification create(UUID notificationId, Channel channel,
      String recipient, String templateName, Priority priority, Instant expiresAt,
      Instant now) {
    return new BufferedNotification(
        UUID.randomUUID(), notificationId, channel, recipient, templateName,
        priority, AggregationBufferStatus.BUFFERED, expiresAt, null, 0, now
    );
  }

  public static BufferedNotification reconstitute(UUID id, UUID notificationId,
      Channel channel, String recipient, String templateName, Priority priority,
      AggregationBufferStatus status, Instant expiresAt, Instant claimedAt,
      int attempts, Instant createdAt) {
    return new BufferedNotification(
        id, notificationId, channel, recipient, templateName, priority, status,
        expiresAt, claimedAt, attempts, createdAt
    );
  }

  /** Returns the grouping key this buffered notification belongs to. */
  public AggregationGroupKey groupKey(boolean requireSameTemplate) {
    return AggregationGroupKey.of(channel, recipient, templateName, requireSameTemplate);
  }

  /**
   * Transitions this row to {@code CLAIMED}, stamping the claim time and
   * incrementing the attempt count (B3 claim phase). Whether this transition
   * is actually allowed to win — i.e. single-claimant semantics against
   * concurrent claimers — is a persistence-layer conditional-update concern,
   * not a domain concern; this method only computes the intended next state.
   */
  public BufferedNotification claim(Instant claimedAtNow) {
    return new BufferedNotification(id, notificationId, channel, recipient, templateName,
        priority, AggregationBufferStatus.CLAIMED, expiresAt, claimedAtNow, attempts + 1,
        createdAt);
  }

  /** Transitions this row to {@code DONE} — its group has been flushed. */
  public BufferedNotification resolve() {
    return new BufferedNotification(id, notificationId, channel, recipient, templateName,
        priority, AggregationBufferStatus.DONE, expiresAt, claimedAt, attempts, createdAt);
  }

  /**
   * Whether this row has exhausted its claim attempts and must be forced onto
   * the individual-delivery path regardless of summarizer availability
   * (poison-group guard, B3).
   */
  public boolean hasExceededMaxAttempts(int maxAttempts) {
    return attempts > maxAttempts;
  }

  public UUID getId() {
    return id;
  }

  public UUID getNotificationId() {
    return notificationId;
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

  public Priority getPriority() {
    return priority;
  }

  public AggregationBufferStatus getStatus() {
    return status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getClaimedAt() {
    return claimedAt;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
