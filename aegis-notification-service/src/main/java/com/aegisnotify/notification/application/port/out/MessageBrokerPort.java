package com.aegisnotify.notification.application.port.out;

import java.util.Map;

public interface MessageBrokerPort {

  void publish(String topic, Map<String, Object> payload);
}
