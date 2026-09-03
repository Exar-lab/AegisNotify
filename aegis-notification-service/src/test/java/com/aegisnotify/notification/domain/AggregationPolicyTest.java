package com.aegisnotify.notification.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.Priority;
import com.aegisnotify.notification.domain.model.AggregationPolicy;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AggregationPolicyTest {

  private static AggregationSettings settings(boolean enabled, Set<String> excludedTemplates,
      Set<Channel> excludedChannels) {
    return new AggregationSettings(enabled, Duration.ofMinutes(5), true, 20,
        excludedTemplates, excludedChannels, Duration.ofMinutes(2), 3);
  }

  @Test
  void isAggregatable_highPriority_neverAggregatable() {
    AggregationSettings settings = settings(true, Set.of(), Set.of());

    assertFalse(AggregationPolicy.isAggregatable(
        Priority.HIGH, Channel.EMAIL, "welcome", settings));
  }

  @Test
  void isAggregatable_mediumPriorityDefaultConfig_isAggregatable() {
    AggregationSettings settings = settings(true, Set.of(), Set.of());

    assertTrue(AggregationPolicy.isAggregatable(
        Priority.MEDIUM, Channel.EMAIL, "welcome", settings));
  }

  @Test
  void isAggregatable_lowPriorityDefaultConfig_isAggregatable() {
    AggregationSettings settings = settings(true, Set.of(), Set.of());

    assertTrue(AggregationPolicy.isAggregatable(
        Priority.LOW, Channel.EMAIL, "welcome", settings));
  }

  @Test
  void isAggregatable_excludedTemplate_neverAggregatable() {
    AggregationSettings settings = settings(true, Set.of("regulated-notice"), Set.of());

    assertFalse(AggregationPolicy.isAggregatable(
        Priority.MEDIUM, Channel.EMAIL, "regulated-notice", settings));
  }

  @Test
  void isAggregatable_excludedTemplate_caseInsensitiveTrimmedMatch() {
    AggregationSettings settings = settings(true, Set.of(" Regulated-Notice "), Set.of());

    assertFalse(AggregationPolicy.isAggregatable(
        Priority.MEDIUM, Channel.EMAIL, "regulated-notice", settings));
  }

  @Test
  void isAggregatable_excludedChannel_neverAggregatable() {
    AggregationSettings settings = settings(true, Set.of(), Set.of(Channel.SMS));

    assertFalse(AggregationPolicy.isAggregatable(
        Priority.MEDIUM, Channel.SMS, "welcome", settings));
  }

  @Test
  void isAggregatable_blankTemplateName_excludedFailSafe() {
    AggregationSettings settings = settings(true, Set.of(), Set.of());

    assertFalse(AggregationPolicy.isAggregatable(
        Priority.MEDIUM, Channel.EMAIL, "   ", settings));
  }

  @Test
  void isAggregatable_nullTemplateName_excludedFailSafe() {
    AggregationSettings settings = settings(true, Set.of(), Set.of());

    assertFalse(AggregationPolicy.isAggregatable(
        Priority.MEDIUM, Channel.EMAIL, null, settings));
  }

  @Test
  void isAggregatable_globallyDisabled_neverAggregatable() {
    AggregationSettings settings = settings(false, Set.of(), Set.of());

    assertFalse(AggregationPolicy.isAggregatable(
        Priority.MEDIUM, Channel.EMAIL, "welcome", settings));
  }
}
