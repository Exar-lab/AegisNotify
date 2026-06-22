package com.aegisnotify.audit.application.dto;

import com.aegisnotify.audit.domain.enums.AuditStatus;
import com.aegisnotify.audit.domain.enums.Channel;
import java.time.Instant;

public record AuditSearchQuery(
    AuditStatus status,
    Channel channel,
    Instant from,
    Instant to,
    int page,
    int size
) {
}
