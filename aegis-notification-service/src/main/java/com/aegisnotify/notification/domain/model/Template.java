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

  /**
   * Creates a brand-new template: generated id, {@code active=true}, and
   * {@code createdAt == updatedAt == now}.
   *
   * @param name unique template name (immutable after creation)
   * @param channel delivery channel (immutable after creation)
   * @param subject optional subject line, may be {@code null}
   * @param body template body
   * @param variables declared variable names, {@code null} treated as empty
   * @return the new template
   */
  public static Template create(String name, Channel channel, String subject,
      String body, List<String> variables) {
    Instant now = Instant.now();
    return new Template(
        UUID.randomUUID(), name, channel, subject, body,
        copyOf(variables), true, now, now
    );
  }

  public static Template reconstitute(UUID id, String name, Channel channel,
      String subject, String body, List<String> variables, boolean active,
      Instant createdAt, Instant updatedAt) {
    return new Template(
        id, name, channel, subject, body,
        List.copyOf(variables), active, createdAt, updatedAt
    );
  }

  /**
   * Returns a copy with new editable content and a bumped {@code updatedAt}.
   * {@code name} and {@code channel} are immutable and carried over as-is.
   *
   * @param newSubject optional subject line, may be {@code null}
   * @param newBody template body
   * @param newVariables declared variable names, {@code null} treated as empty
   * @return the updated copy
   */
  public Template withContent(String newSubject, String newBody, List<String> newVariables) {
    return new Template(
        id, name, channel, newSubject, newBody,
        copyOf(newVariables), active, createdAt, Instant.now()
    );
  }

  /**
   * Returns a copy with the requested active state. Idempotent: when the
   * template is already in that state, this same instance is returned
   * unchanged (no {@code updatedAt} bump).
   *
   * @param newActive the desired active state
   * @return this instance if unchanged, otherwise an updated copy
   */
  public Template withActive(boolean newActive) {
    if (active == newActive) {
      return this;
    }
    return new Template(
        id, name, channel, subject, body,
        variables, newActive, createdAt, Instant.now()
    );
  }

  private static List<String> copyOf(List<String> variables) {
    return variables == null ? List.of() : List.copyOf(variables);
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
