package com.aegisnotify.notification.application.service;

/**
 * Notification log texts written by the aggregation flush path that other
 * code (the status read model) recognizes. Kept in one place so the writer
 * and the reader cannot drift apart.
 */
final class AggregationLogMessages {

  /** Prefix of every log entry written when a row is released for individual delivery. */
  static final String RELEASED_INDIVIDUALLY_PREFIX =
      "Released from aggregation buffer for individual delivery";

  private AggregationLogMessages() {
  }
}
