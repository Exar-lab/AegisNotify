package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.TemplateResponse;
import com.aegisnotify.notification.application.dto.UpdateTemplateCommand;
import java.util.UUID;

public interface UpdateTemplateUseCase {

  TemplateResponse update(UUID id, UpdateTemplateCommand command);
}
