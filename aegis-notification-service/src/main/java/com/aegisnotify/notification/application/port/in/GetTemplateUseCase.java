package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.TemplateResponse;
import java.util.UUID;

public interface GetTemplateUseCase {

  TemplateResponse get(UUID id);
}
