package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.domain.model.OutboxEvent;

public interface OutboxEventRepository {

  OutboxEvent save(OutboxEvent event);
}
