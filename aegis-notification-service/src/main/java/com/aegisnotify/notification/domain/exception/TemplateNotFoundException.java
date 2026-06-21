package com.aegisnotify.notification.domain.exception;

public final class TemplateNotFoundException extends DomainException {

  public TemplateNotFoundException(String templateName) {
    super(String.format("Template not found: %s", templateName));
  }
}
