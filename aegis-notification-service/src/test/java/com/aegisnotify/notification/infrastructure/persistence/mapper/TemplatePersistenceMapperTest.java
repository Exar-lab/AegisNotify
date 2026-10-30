package com.aegisnotify.notification.infrastructure.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.model.Template;
import com.aegisnotify.notification.infrastructure.persistence.entity.TemplateJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplatePersistenceMapperTest {

  private final TemplatePersistenceMapper mapper = new TemplatePersistenceMapper();

  @Test
  void toDomain_nullVariables_returnsEmptyList() {
    TemplateJpaEntity entity = new TemplateJpaEntity(
        UUID.randomUUID(), "legacy-template", Channel.EMAIL, "Subject",
        "Hello {{name}}", null, true, Instant.now(), Instant.now(), 1L);

    Template result = mapper.toDomain(entity);

    assertTrue(result.getVariables().isEmpty());
  }

  @Test
  void toDomain_nonNullVariables_returnsCopiedList() {
    TemplateJpaEntity entity = new TemplateJpaEntity(
        UUID.randomUUID(), "order-confirmation", Channel.EMAIL, "Subject",
        "Hello {{name}}, order {{orderId}}", List.of("name", "orderId"), true,
        Instant.now(), Instant.now(), 1L);

    Template result = mapper.toDomain(entity);

    assertEquals(List.of("name", "orderId"), result.getVariables());
  }

  @Test
  void toDomain_carriesEntityVersion() {
    TemplateJpaEntity entity = new TemplateJpaEntity(
        UUID.randomUUID(), "welcome", Channel.EMAIL, "Subject",
        "Hello {{name}}", List.of("name"), true, Instant.now(), Instant.now(), 5L);

    Template result = mapper.toDomain(entity);

    assertEquals(5L, result.getVersion());
  }

  @Test
  void toJpa_carriesDomainVersion() {
    // Must round-trip: the @Version-annotated column is how Hibernate
    // detects a lost update between two concurrent edits (optimistic
    // locking). Dropping it here on save would silently defeat that check.
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL, "Subject",
        "Hello {{name}}", List.of("name"), true, Instant.now(), Instant.now(), 5L);

    TemplateJpaEntity result = mapper.toJpa(template);

    assertEquals(5L, result.getVersion());
  }
}
