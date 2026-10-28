package com.aegisnotify.notification.domain.exception;

import java.util.UUID;

public final class TemplateNotFoundException extends DomainException {

  public TemplateNotFoundException(String templateName) {
    super(String.format("Template not found: %s", templateName));
  }

  public TemplateNotFoundException(UUID id) {
    super(String.format("Template not found: %s", id));
  }
}
