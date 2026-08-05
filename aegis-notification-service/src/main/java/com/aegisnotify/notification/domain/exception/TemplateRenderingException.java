package com.aegisnotify.notification.domain.exception;

public final class TemplateRenderingException extends DomainException {

  public TemplateRenderingException(String message) {
    super(message);
  }

  public TemplateRenderingException(String message, Throwable cause) {
    super(message, cause);
  }
}
