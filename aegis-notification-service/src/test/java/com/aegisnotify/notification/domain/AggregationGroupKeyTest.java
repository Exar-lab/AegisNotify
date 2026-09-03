package com.aegisnotify.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.model.AggregationGroupKey;
import com.aegisnotify.notification.domain.model.AggregationPolicy;
import com.aegisnotify.notification.domain.model.AggregationSettings;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AggregationGroupKeyTest {

  private static AggregationSettings settings(boolean requireSameTemplate) {
    return new AggregationSettings(true, Duration.ofMinutes(5), requireSameTemplate, 20,
        Set.of(), Set.of(), Duration.ofMinutes(2), 3);
  }

  @Test
  void groupKeyFor_differentRecipients_neverEqual() {
    AggregationGroupKey key1 = AggregationPolicy.groupKeyFor(
        Channel.EMAIL, "a@example.com", "welcome", settings(true));
    AggregationGroupKey key2 = AggregationPolicy.groupKeyFor(
        Channel.EMAIL, "b@example.com", "welcome", settings(true));

    assertNotEquals(key1, key2);
  }

  @Test
  void groupKeyFor_sameRecipientChannelTemplate_defaultConfig_equal() {
    AggregationGroupKey key1 = AggregationPolicy.groupKeyFor(
        Channel.EMAIL, "a@example.com", "welcome", settings(true));
    AggregationGroupKey key2 = AggregationPolicy.groupKeyFor(
        Channel.EMAIL, "a@example.com", "welcome", settings(true));

    assertEquals(key1, key2);
  }

  @Test
  void groupKeyFor_requireSameTemplateFalse_ignoresTemplate() {
    AggregationGroupKey key1 = AggregationPolicy.groupKeyFor(
        Channel.EMAIL, "a@example.com", "welcome", settings(false));
    AggregationGroupKey key2 = AggregationPolicy.groupKeyFor(
        Channel.EMAIL, "a@example.com", "invoice", settings(false));

    assertEquals(key1, key2);
    assertNull(key1.templateName());
  }

  @Test
  void groupKeyFor_requireSameTemplateTrue_differentTemplates_notEqual() {
    AggregationGroupKey key1 = AggregationPolicy.groupKeyFor(
        Channel.EMAIL, "a@example.com", "welcome", settings(true));
    AggregationGroupKey key2 = AggregationPolicy.groupKeyFor(
        Channel.EMAIL, "a@example.com", "invoice", settings(true));

    assertNotEquals(key1, key2);
  }
}
