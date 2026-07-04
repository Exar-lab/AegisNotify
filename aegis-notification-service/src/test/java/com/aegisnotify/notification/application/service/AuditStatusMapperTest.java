package com.aegisnotify.notification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aegisnotify.notification.domain.enums.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AuditStatusMapperTest {

  @Test
  void toAuditStatus_pending_returnsPending() {
    assertEquals("PENDING", AuditStatusMapper.toAuditStatus(NotificationStatus.PENDING));
  }

  @Test
  void toAuditStatus_queued_returnsQueued() {
    assertEquals("QUEUED", AuditStatusMapper.toAuditStatus(NotificationStatus.QUEUED));
  }

  @Test
  void toAuditStatus_processing_returnsProcessing() {
    assertEquals("PROCESSING", AuditStatusMapper.toAuditStatus(NotificationStatus.PROCESSING));
  }

  @Test
  void toAuditStatus_sent_returnsSent() {
    assertEquals("SENT", AuditStatusMapper.toAuditStatus(NotificationStatus.SENT));
  }

  @Test
  void toAuditStatus_sentViaFallback_returnsSentViaFallback() {
    assertEquals("SENT_VIA_FALLBACK",
        AuditStatusMapper.toAuditStatus(NotificationStatus.SENT_VIA_FALLBACK));
  }

  @Test
  void toAuditStatus_failed_returnsFailed() {
    assertEquals("FAILED", AuditStatusMapper.toAuditStatus(NotificationStatus.FAILED));
  }

  @Test
  void toAuditStatus_failedCritical_returnsFailedCritical() {
    assertEquals("FAILED_CRITICAL",
        AuditStatusMapper.toAuditStatus(NotificationStatus.FAILED_CRITICAL));
  }

  @Test
  void toAuditStatus_cancelled_returnsCancelled() {
    assertEquals("CANCELLED", AuditStatusMapper.toAuditStatus(NotificationStatus.CANCELLED));
  }

  @Test
  void toProviderFailStatus_primaryFail_returnsProviderFailA() {
    assertEquals("PROVIDER_A_FAIL", AuditStatusMapper.toProviderFailStatus(true));
  }

  @Test
  void toProviderFailStatus_fallbackFail_returnsProviderFailB() {
    assertEquals("PROVIDER_B_FAIL", AuditStatusMapper.toProviderFailStatus(false));
  }

  @ParameterizedTest
  @EnumSource(NotificationStatus.class)
  void toAuditStatus_allEnumValues_neverThrows(NotificationStatus status) {
    String result = AuditStatusMapper.toAuditStatus(status);
    assertEquals(status.name(), result);
  }
}
