package com.aegisnotify.notification.domain.enums;

/**
 * What part a notification played in the aggregation feature (issue #225).
 * Derived on read; nothing new is persisted for it.
 */
public enum AggregationRole {
  /** Never aggregated (including a notification still held in the buffer). */
  NONE,
  /** Carries the summarized body and is the only member actually delivered. */
  LEADER,
  /** Folded into an aggregate led by another notification; no provider call of its own. */
  SIBLING,
  /** Left the aggregation buffer and was delivered as its own notification. */
  RELEASED_INDIVIDUALLY
}
