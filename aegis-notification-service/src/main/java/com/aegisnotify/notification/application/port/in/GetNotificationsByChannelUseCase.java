package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.NotificationSummary;
import com.aegisnotify.notification.domain.enums.Channel;
import java.util.List;

public interface GetNotificationsByChannelUseCase {

  List<NotificationSummary> getByChannel(Channel channel);
}
