package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.domain.model.OutboxEvent;
import java.util.List;

public interface OutboxEventRepository {

  OutboxEvent save(OutboxEvent event);

  List<OutboxEvent> findPendingEvents();
}
