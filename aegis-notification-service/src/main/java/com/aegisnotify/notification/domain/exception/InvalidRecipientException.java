package com.aegisnotify.notification.domain.exception;

import com.aegisnotify.notification.domain.enums.Channel;

public final class InvalidRecipientException extends DomainException {

  public InvalidRecipientException(Channel channel, String recipient) {
    super(String.format("Invalid recipient '%s' for channel %s", recipient, channel));
  }
}
