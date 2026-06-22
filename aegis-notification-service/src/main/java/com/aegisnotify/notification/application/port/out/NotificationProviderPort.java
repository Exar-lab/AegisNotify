package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.domain.enums.Channel;

public interface NotificationProviderPort {

  ProviderResult send(Channel channel, String recipient, String renderedContent, String subject);
}
