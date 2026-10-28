package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.CreateTemplateCommand;
import com.aegisnotify.notification.application.dto.TemplateResponse;

public interface CreateTemplateUseCase {

  TemplateResponse create(CreateTemplateCommand command);
}
