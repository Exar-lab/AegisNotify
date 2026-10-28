package com.aegisnotify.notification.domain.exception;

/**
 * Raised when creating a template whose name (case-sensitive exact match)
 * is already taken. Mapped to 409 Conflict by the web layer.
 */
public final class DuplicateTemplateNameException extends DomainException {

  public DuplicateTemplateNameException(String templateName) {
    super(String.format("Template name already exists: %s", templateName));
  }
}
