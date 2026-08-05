package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.application.dto.TemplateRenderRequest;

public interface TemplateRenderer {

  String render(TemplateRenderRequest request);
}
