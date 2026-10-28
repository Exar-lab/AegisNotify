package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.TemplateResponse;
import java.util.UUID;

public interface SetTemplateStatusUseCase {

  TemplateResponse setStatus(UUID id, boolean active);
}
