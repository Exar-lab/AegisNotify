package com.aegisnotify.notification.domain.model;

import com.aegisnotify.notification.domain.enums.Channel;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Template {

  private final UUID id;
  private final String name;
  private final Channel channel;
  private final String subject;
  private final String body;
  private final List<String> variables;
  private final boolean active;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Template(UUID id, String name, Channel channel, String subject,
      String body, List<String> variables, boolean active,
      Instant createdAt, Instant updatedAt) {
    this.id = id;
    this.name = name;
    this.channel = channel;
    this.subject = subject;
    this.body = body;
    this.variables = variables;
    this.active = active;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Template reconstitute(UUID id, String name, Channel channel,
      String subject, String body, List<String> variables, boolean active,
      Instant createdAt, Instant updatedAt) {
    return new Template(
        id, name, channel, subject, body,
        List.copyOf(variables), active, createdAt, updatedAt
    );
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Channel getChannel() {
    return channel;
  }

  public String getSubject() {
    return subject;
  }

  public String getBody() {
    return body;
  }

  public List<String> getVariables() {
    return Collections.unmodifiableList(variables);
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
