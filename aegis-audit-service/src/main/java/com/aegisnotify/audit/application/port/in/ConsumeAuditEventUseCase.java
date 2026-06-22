package com.aegisnotify.audit.application.port.in;

import com.aegisnotify.audit.application.dto.AuditEventCommand;

public interface ConsumeAuditEventUseCase {

  void consume(AuditEventCommand command);
}
