package com.aegisnotify.notification.infrastructure.persistence.entity;

import com.aegisnotify.notification.domain.enums.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "templates")
public class TemplateJpaEntity {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Channel channel;

  private String subject;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(columnDefinition = "text[]")
  private List<String> variables;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TemplateJpaEntity() {
  }

  public TemplateJpaEntity(UUID id, String name, Channel channel,
      String subject, String body, List<String> variables, boolean active,
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
    return variables;
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
