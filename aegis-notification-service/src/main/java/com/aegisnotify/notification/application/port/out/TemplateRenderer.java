package com.aegisnotify.notification.application.port.out;

import java.util.Map;

public interface TemplateRenderer {

  String render(String templateBody, Map<String, Object> parameters);
}
