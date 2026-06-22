package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.RenderTemplateCommand;
import com.aegisnotify.notification.application.dto.RenderedTemplateResponse;

public interface RenderTemplateUseCase {

  RenderedTemplateResponse render(RenderTemplateCommand command);
}
