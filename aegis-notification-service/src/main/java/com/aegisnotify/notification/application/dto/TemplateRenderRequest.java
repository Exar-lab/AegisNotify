package com.aegisnotify.notification.application.dto;

import com.aegisnotify.notification.domain.enums.Channel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TemplateRenderRequest(
    String templateBody,
    Map<String, Object> parameters,
    List<String> requiredVariables,
    Channel channel) {

  public TemplateRenderRequest {
    Objects.requireNonNull(templateBody, "templateBody");
    Objects.requireNonNull(channel, "channel");
    parameters = parameters == null
        ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    requiredVariables = requiredVariables == null ? List.of() : List.copyOf(requiredVariables);
  }
}
